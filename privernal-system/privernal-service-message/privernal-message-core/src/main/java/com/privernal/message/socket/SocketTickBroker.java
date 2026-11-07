/*
 * Copyright 2025-2026 saperliu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.privernal.message.socket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 大屏实时通道（M6.5）：按频道发「该刷新了」的节拍，<b>不携带任何数据</b>。
 * <p>
 * <b>为什么数据不走 WS。</b> 一条聚合结果对谁长什么样，取决于三个每人不同的东西：
 * 他的行级数据权限、他当前的大屏参数、他的下钻层级。服务端算一次再广播，等于把这三件事
 * 强行统一 —— 那不是优化，是越权读取。所以订阅只登记节拍，取数仍由客户端走 HTTP，
 * 带着自己的登录上下文打 {@code /back/datav/data/query}，行级权限与参数天然各算各的。
 * <p>
 * <b>因此本类有一条安全不变式：频道里绝不能出现业务数据。</b> 订阅本身不做数据权限校验
 * （握手期只校验了 token 合法性），一旦有人把数值塞进 tick 载荷，就等于开了一条
 * 绕过行级权限的读取通道。载荷只有频道名、时间戳与序号，正是为了让人一眼看出它不该变多。
 * <p>
 * <b>那 WS 比客户端自己 {@code setInterval} 强在哪。</b> 强在节拍由服务端统一发：同一块屏的
 * 一百个观众在同一毫秒被叫醒，{@link DatavResultCache 结果缓存}（按身份+配置+参数键）就能把
 * 这一百次查询收敛成一次；各客户端自己错开轮询时，缓存帮不上忙。其次是过载时服务端可以
 * 直接拒发新订阅（客户端退回轮询），而不是让所有人一起把库打穿。
 * <p>
 * 时钟走 {@link #nowMillis()} 缝隙，与 {@code DatavQueryBreaker} 同一套测法：用例注入假时钟，
 * 不 sleep、不起 Spring。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class SocketTickBroker {

    /** 订阅结果：拒绝时必须带上原因，客户端要据此决定退回轮询还是提示 */
    public record Subscription(boolean accepted, long intervalMillis, String reason) {

        static Subscription ok(long intervalMillis) {
            return new Subscription(true, intervalMillis, null);
        }

        static Subscription rejected(String reason) {
            return new Subscription(false, 0, reason);
        }
    }

    /** 一个频道到期的节拍载荷 */
    public record DueTick(String channel, long atMillis, long seq) {

        /** 载荷里没有数据，只有节拍标识 —— 见类注释的安全不变式 */
        public Map<String, Object> payload() {
            Map<String, Object> body = new HashMap<>(4);
            body.put("channel", channel);
            body.put("at", atMillis);
            body.put("seq", seq);
            return body;
        }
    }

    private static final class Channel {

        /** 加入顺序即订阅顺序，超上限时先踢最早订阅的（与连接表同一套语义） */
        private final LinkedHashSet<UUID> sessions = new LinkedHashSet<>();

        private long intervalMillis;

        private long nextDueAt;

        private long seq;
    }

    /** 所有对 channels 的读写都在 synchronized(channels) 里，包括内部 Channel 的字段 */
    private final Map<String, Channel> channels = new LinkedHashMap<>();

    /** 总开关：默认关闭，保持「只有客户端轮询」的今日行为 */
    @Value("${socketio.tick.enabled:false}")
    private boolean enabled;

    /**
     * 节拍下限。默认 5 秒：100 块屏 × 每屏 10 个实时组件时，1 秒节拍等于自己造 DDoS，
     * 而且取数本身是几百毫秒级，比这更快的节拍观众看不出差别。
     */
    @Value("${socketio.tick.min-interval-seconds:5}")
    private int minIntervalSeconds;

    /** 单个连接最多订阅几个频道：正常投放只有一块屏，给 8 已经够嵌屏与调试用 */
    @Value("${socketio.tick.max-channels-per-session:8}")
    private int maxChannelsPerSession;

    /** 单个频道最多多少观众：超了先踢最早订阅的 */
    @Value("${socketio.tick.max-sessions-per-channel:500}")
    private int maxSessionsPerChannel;

    /** 服务端同时维护的频道总数上限（≈ 同时被实时投放的大屏块数） */
    @Value("${socketio.tick.max-channels:200}")
    private int maxChannels;

    /** 供测试替换的时钟缝隙 */
    long nowMillis() {
        return System.currentTimeMillis();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 订阅一个频道。
     *
     * @param intervalSeconds 期望节拍；低于下限会被抬到下限（返回值里给出实际生效值）
     */
    public Subscription subscribe(String channel, UUID sessionId, int intervalSeconds) {
        if (!enabled) {
            return Subscription.rejected("实时通道未启用");
        }
        if (channel == null || channel.trim().isEmpty()) {
            return Subscription.rejected("频道名不能为空");
        }
        long interval = Math.max(minIntervalSeconds, intervalSeconds) * 1000L;
        String name = channel.trim();

        synchronized (channels) {
            if (channelsOfSessionLocked(sessionId) >= maxChannelsPerSession
                    && !name.equals(currentOnlyChannel(channels, sessionId))) {
                return Subscription.rejected("单个连接最多订阅 " + maxChannelsPerSession + " 个实时频道");
            }
            Channel existing = channels.get(name);
            if (existing == null && channels.size() >= maxChannels) {
                // 明确拒绝而不是悄悄降频：客户端退回轮询还能刷新，骗它说已订阅会让那块屏永远不动
                return Subscription.rejected("实时频道数已达上限 " + maxChannels + "，请改用轮询刷新");
            }
            Channel target = channels.computeIfAbsent(name, key -> {
                Channel created = new Channel();
                created.intervalMillis = interval;
                created.nextDueAt = nowMillis() + interval;
                return created;
            });
            target.sessions.add(sessionId);
            // 取所有订阅者里最快的节拍：慢订阅者被快订阅者顺带叫醒，不会更旧；反过来则会误事
            target.intervalMillis = Math.min(target.intervalMillis, interval);
            evictBeyondCapacity(target, name);
            return Subscription.ok(target.intervalMillis);
        }
    }

    /** 频道观众超上限时请走最早订阅的那批，而不是拒掉新来的人 */
    private void evictBeyondCapacity(Channel channel, String name) {
        while (channel.sessions.size() > maxSessionsPerChannel) {
            UUID oldest = channel.sessions.iterator().next();
            channel.sessions.remove(oldest);
            log.info("实时频道 {} 观众数超过上限 {}，请出最早订阅的连接 {}", name, maxSessionsPerChannel, oldest);
        }
    }

    public void unsubscribe(String channel, UUID sessionId) {
        if (channel == null) {
            return;
        }
        synchronized (channels) {
            Channel target = channels.get(channel.trim());
            if (target == null) {
                return;
            }
            target.sessions.remove(sessionId);
            if (target.sessions.isEmpty()) {
                // 空频道必须回收，否则每块被打开过的大屏都会永久占一格，上限很快被历史流量吃光
                channels.remove(channel.trim());
            }
        }
    }

    /** 连接断开：把它名下所有频道的登记清掉 */
    public void dropSession(UUID sessionId) {
        List<String> emptied = new ArrayList<>(4);
        synchronized (channels) {
            for (Map.Entry<String, Channel> entry : channels.entrySet()) {
                if (entry.getValue().sessions.remove(sessionId) && entry.getValue().sessions.isEmpty()) {
                    emptied.add(entry.getKey());
                }
            }
            emptied.forEach(channels::remove);
        }
    }

    /**
     * 到期的频道出队并把它们推到下一拍。
     * <p>
     * 由调用方（netty 侧）负责真正发送。这里<b>不排队补发</b>：错过的那一拍直接作废 ——
     * 观众要的是「现在的新数据」，补发一串过期节拍只会让 HTTP 端排队积压，
     * 表现成「屏越卡越猛」。
     */
    public List<DueTick> pollDue() {
        if (!enabled) {
            return List.of();
        }
        long now = nowMillis();
        List<DueTick> due = new ArrayList<>(4);
        synchronized (channels) {
            for (Map.Entry<String, Channel> entry : channels.entrySet()) {
                Channel channel = entry.getValue();
                if (channel.sessions.isEmpty() || channel.nextDueAt > now) {
                    continue;
                }
                channel.seq++;
                due.add(new DueTick(entry.getKey(), now, channel.seq));
                channel.nextDueAt = now + channel.intervalMillis;
            }
        }
        return due;
    }

    /** 当前登记的频道数（监控与用例用） */
    public int channelCount() {
        synchronized (channels) {
            return channels.size();
        }
    }

    /** 某频道当前的观众数 */
    public int subscriberCount(String channel) {
        synchronized (channels) {
            Channel target = channels.get(channel);
            return target == null ? 0 : target.sessions.size();
        }
    }

    /** 某连接订阅了哪些频道（断开时显式退房间用） */
    public List<String> channelsOf(UUID sessionId) {
        synchronized (channels) {
            List<String> names = new ArrayList<>(2);
            for (Map.Entry<String, Channel> entry : channels.entrySet()) {
                if (entry.getValue().sessions.contains(sessionId)) {
                    names.add(entry.getKey());
                }
            }
            return names;
        }
    }

    /** 单个连接当前订阅了几个频道（未订阅时返回 0，重复订阅同一频道不计第二次） */
    public int channelsOfSession(UUID sessionId) {
        synchronized (channels) {
            return channelsOfSessionLocked(sessionId);
        }
    }

    /**
     * 上限判定要在锁内做，且**重复订阅同一频道不能把自己挤爆**：
     * 观众刷新一次页面就会重新订阅一次，若按次数算，刷八次屏之后这块屏就再也订不上了。
     */
    private int channelsOfSessionLocked(UUID sessionId) {
        int count = 0;
        for (Channel channel : channels.values()) {
            if (channel.sessions.contains(sessionId)) {
                count++;
            }
        }
        return count;
    }

    /** 已订阅的唯一频道名，用于放行「重复订阅同一频道」 */
    private static String currentOnlyChannel(Map<String, Channel> all, UUID sessionId) {
        String found = null;
        for (Map.Entry<String, Channel> entry : all.entrySet()) {
            if (!entry.getValue().sessions.contains(sessionId)) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = entry.getKey();
        }
        return found;
    }
}
