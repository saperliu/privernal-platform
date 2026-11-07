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

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 大屏实时节拍的 netty 端点（M6.5）。
 * <p>
 * 只做三件事：登记订阅、按 {@link SocketTickBroker} 的到期表向房间发 {@code datav_tick}、
 * 断开时清登记。<b>载荷里永远没有业务数据</b>（理由见 {@link SocketTickBroker} 的安全不变式），
 * 所以这里不需要登录上下文、也不查库 —— 数据仍由客户端拿着自己的 token 走 HTTP 取。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class NettySocketTickEndpoint {

    /** 与前端 useDatavRealtime 的事件名同一份口径 */
    public static final String EVENT_SUBSCRIBE = "datav_subscribe";

    public static final String EVENT_UNSUBSCRIBE = "datav_unsubscribe";

    public static final String EVENT_TICK = "datav_tick";

    @Autowired
    private SocketIOServer socketIOServer;

    @Autowired
    private SocketTickBroker tickBroker;

    /** 扫描间隔：节拍本身的最小粒度，1 秒足够（节拍下限是 5 秒） */
    @Value("${socketio.tick.scan-millis:1000}")
    private int scanMillis;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    private void startTicker() {
        if (!tickBroker.isEnabled()) {
            // 不起线程：默认关闭时必须与今天完全一致，不留下一个每秒空转的定时器
            log.info("大屏实时通道未启用（socketio.tick.enabled=false）");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "datav-tick");
            thread.setDaemon(true);
            return thread;
        });
        long period = Math.max(200, scanMillis);
        // fixedDelay 而不是 fixedRate：某一拍发得慢就顺延，不补发。节拍补发只会让 HTTP 端排队积压
        scheduler.scheduleWithFixedDelay(this::emitDueTicks, period, period, TimeUnit.MILLISECONDS);
        log.info("大屏实时通道已启动，扫描间隔 {}ms", period);
    }

    @PreDestroy
    private void stopTicker() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void emitDueTicks() {
        try {
            for (SocketTickBroker.DueTick tick : tickBroker.pollDue()) {
                socketIOServer.getRoomOperations(tick.channel()).sendEvent(EVENT_TICK, tick.payload());
            }
        } catch (RuntimeException e) {
            // 定时器一旦抛出异常就会静默停掉，之后所有实时屏都停在最后一拍上且没有任何提示，
            // 所以这里必须兜住并留下痕迹
            log.error("大屏实时节拍发送失败，本轮已跳过", e);
        }
    }

    @OnEvent(EVENT_SUBSCRIBE)
    public void onSubscribe(SocketIOClient client, AckRequest ack, Map<String, Object> body) {
        String channel = body == null ? "" : String.valueOf(body.getOrDefault("channel", ""));
        int interval = toInt(body == null ? null : body.get("intervalSec"), 0);
        UUID sessionId = client.getSessionId();
        SocketTickBroker.Subscription result = tickBroker.subscribe(channel, sessionId, interval);
        if (result.accepted()) {
            client.joinRoom(channel.trim());
        }
        if (ack.isAckRequested()) {
            ack.sendAckData(ackOf(result));
        }
    }

    @OnEvent(EVENT_UNSUBSCRIBE)
    public void onUnsubscribe(SocketIOClient client, Map<String, Object> body) {
        String channel = body == null ? "" : String.valueOf(body.getOrDefault("channel", ""));
        UUID sessionId = client.getSessionId();
        tickBroker.unsubscribe(channel, sessionId);
        client.leaveRoom(channel.trim());
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        List<String> channels = tickBroker.channelsOf(client.getSessionId());
        tickBroker.dropSession(client.getSessionId());
        // 房间成员随连接销毁而失效，但显式退出更干净：同一 JVM 里连接被复用时不留幽灵订阅
        channels.forEach(client::leaveRoom);
    }

    private static Map<String, Object> ackOf(SocketTickBroker.Subscription result) {
        Map<String, Object> ack = new HashMap<>(4);
        ack.put("accepted", result.accepted());
        ack.put("intervalSec", result.intervalMillis() / 1000);
        ack.put("reason", result.reason());
        return ack;
    }

    private static int toInt(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return raw == null ? fallback : Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
