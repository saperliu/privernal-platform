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

import com.corundumstudio.socketio.SocketIOClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在线 socket 连接的登记表。
 * <p>
 * 三件事是原来没有的，而 M6.5 要在这同一个 netty 服务上跑上百块大屏，缺一件都会出事：
 * <ul>
 *   <li><b>会话表原本是 {@code HashMap} 塞进 {@code ConcurrentHashMap}</b>。同一个人开两个页时，
 *       两个线程各自 {@code get} 到 null 或旧表、都 new 一个再 {@code put}，后写的把先写的整张覆盖；
 *       被覆盖的那条连接既收不到推送，也永远不会被 {@code @OnDisconnect} 摘掉——它只留在
 *       worker 线程上。现在整张表按用户加锁，插入序就是连接先后序。</li>
 *   <li><b>单用户上限与淘汰</b>。浏览器被强杀、网络抖动时 {@code @OnDisconnect} 不保证到达，
 *       没有上限就会一路堆到 worker 耗尽。超限踢的是<b>最老</b>的那条：观众正在看的永远是最新的。</li>
 *   <li><b>总量上限</b>。超限拒的是<b>新</b>连接，绝不动已经在看屏的人——把在线的人踢掉去腾位置
 *       会让故障表现成「所有人的屏都在闪」，比拒掉一个新访问者严重得多。</li>
 * </ul>
 *
 * @author saperliu
 */
@Slf4j
@Component
public class SocketClientService {

    /**
     * 单用户同时最多保留几条连接，0 表示不限（保持历史行为）。
     * <p>
     * 建议生产值 6：一个人常开 3~4 个页（后台 + 大屏 + 手机），再往上基本都是断线重连留下的僵尸连接。
     */
    @Value("${socketio.max-sessions-per-user:0}")
    private int maxSessionsPerUser;

    /**
     * 服务端总连接上限，0 表示不限（保持历史行为）。
     * <p>
     * 建议生产值 2000：{@code workCount: 100} 下每条连接都是常驻的，量级要按「在线人数 × 页签数」算，
     * 而不是按「块数」算。
     */
    @Value("${socketio.max-connections:0}")
    private int maxConnections;

    private final ConcurrentHashMap<String, UserSessions> users = new ConcurrentHashMap<>();

    private final AtomicInteger total = new AtomicInteger();

    /**
     * 登记一条连接。
     *
     * @return false 表示总量超限被拒；调用方必须把这条连接断开，否则它照样占着 worker 线程
     */
    public boolean saveClient(String userId, UUID sessionId, SocketIOClient client) {
        UserSessions sessions = users.computeIfAbsent(userId, key -> new UserSessions());
        boolean added;
        synchronized (sessions) {
            added = sessions.add(sessionId, client);
        }
        if (!added) {
            return false;
        }
        if (maxSessionsPerUser > 0) {
            evictStaleSessions(userId, sessions);
        }
        return true;
    }

    /** 踢掉该用户最老的连接，直到回到上限之内 */
    private void evictStaleSessions(String userId, UserSessions sessions) {
        List<SocketIOClient> evicted = new ArrayList<>();
        synchronized (sessions) {
            while (sessions.size() > maxSessionsPerUser) {
                SocketIOClient oldest = sessions.removeOldest();
                if (oldest == null) {
                    break;
                }
                evicted.add(oldest);
            }
        }
        for (SocketIOClient client : evicted) {
            total.decrementAndGet();
            // 先摘表再断开：disconnect 会触发 @OnDisconnect，那里也会尝试摘同一条记录
            client.disconnect();
        }
        if (!evicted.isEmpty()) {
            log.warn("用户 {} 的连接数超过上限 {}，已断开最早的 {} 条", userId, maxSessionsPerUser, evicted.size());
        }
    }

    /**
     * 取某个用户当前的连接快照。
     * <p>
     * 返回副本而不是内部表：调用方（推送循环）会遍历它，边遍历边被 {@code @OnDisconnect} 修改
     * 会抛 ConcurrentModificationException——那种异常发生在推送线程上，症状是「消息偶尔发不出去」。
     */
    public Map<UUID, SocketIOClient> getUserClient(String userId) {
        UserSessions sessions = users.get(userId);
        if (sessions == null) {
            return Map.of();
        }
        synchronized (sessions) {
            return new LinkedHashMap<>(sessions.clientsView());
        }
    }

    /** 所有用户的连接快照，平台广播用 */
    public Collection<Map<UUID, SocketIOClient>> getAllClient() {
        List<Map<UUID, SocketIOClient>> snapshot = new ArrayList<>(users.size());
        for (UserSessions sessions : users.values()) {
            synchronized (sessions) {
                if (!sessions.isEmpty()) {
                    snapshot.add(new LinkedHashMap<>(sessions.clientsView()));
                }
            }
        }
        return snapshot;
    }

    /** 删除用户某个页面的连接 */
    public void deleteSessionClientByUserId(String userId, UUID sessionId) {
        UserSessions sessions = users.get(userId);
        if (sessions == null) {
            return;
        }
        boolean removed;
        synchronized (sessions) {
            removed = sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                // 空壳必须一起摘掉，否则 users 这张表会随登录过的用户数单调增长
                users.remove(userId, sessions);
            }
        }
        if (removed) {
            total.decrementAndGet();
        }
    }

    /** 删除某个用户的全部连接（踢人下线、改密之后） */
    public void deleteUserCacheByUserId(String userId) {
        UserSessions sessions = users.remove(userId);
        if (sessions == null) {
            return;
        }
        int dropped;
        synchronized (sessions) {
            dropped = sessions.size();
            sessions.clear();
        }
        total.addAndGet(-dropped);
    }

    /** 当前登记的连接总数 */
    public int connectionCount() {
        return total.get();
    }

    /** 插入序 = 连接先后序，所以淘汰最老的只要取 LinkedHashMap 的第一个 */
    private final class UserSessions {

        private final LinkedHashMap<UUID, SocketIOClient> clients = new LinkedHashMap<>();

        boolean add(UUID sessionId, SocketIOClient client) {
            if (maxConnections > 0 && total.get() >= maxConnections) {
                log.warn("socket 连接总数已达上限 {}，拒绝新连接", maxConnections);
                return false;
            }
            clients.put(sessionId, client);
            total.incrementAndGet();
            return true;
        }

        boolean remove(UUID sessionId) {
            return clients.remove(sessionId) != null;
        }

        SocketIOClient removeOldest() {
            Map.Entry<UUID, SocketIOClient> oldest = clients.entrySet().stream().findFirst().orElse(null);
            if (oldest == null) {
                return null;
            }
            clients.remove(oldest.getKey());
            return oldest.getValue();
        }

        Map<UUID, SocketIOClient> clientsView() {
            return clients;
        }

        boolean isEmpty() {
            return clients.isEmpty();
        }

        int size() {
            return clients.size();
        }

        void clear() {
            clients.clear();
        }
    }
}
