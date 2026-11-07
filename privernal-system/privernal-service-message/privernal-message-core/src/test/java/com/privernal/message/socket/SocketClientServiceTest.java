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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 连接登记表测试：并发不丢连接、超限淘汰、计数不漂移。
 * <p>
 * 用反射写 {@code @Value} 字段而不是起 Spring 上下文：被测的是登记表的不变式，
 * 起一次容器的成本会让人干脆不写这几个用例。
 *
 * @author saperliu
 */
@DisplayName("socket 连接登记表与连接数治理")
class SocketClientServiceTest {

    private static SocketClientService service(int maxSessionsPerUser, int maxConnections) {
        SocketClientService target = new SocketClientService();
        setField(target, "maxSessionsPerUser", maxSessionsPerUser);
        setField(target, "maxConnections", maxConnections);
        return target;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("同一用户的多个页签都留在表里（旧实现用 HashMap 整张覆盖会丢连接）")
    void keepsEverySessionOfOneUser() {
        SocketClientService registry = service(0, 0);

        assertThat(registry.saveClient("u1", UUID.randomUUID(), mock(SocketIOClient.class))).isTrue();
        assertThat(registry.saveClient("u1", UUID.randomUUID(), mock(SocketIOClient.class))).isTrue();
        assertThat(registry.saveClient("u1", UUID.randomUUID(), mock(SocketIOClient.class))).isTrue();

        assertThat(registry.getUserClient("u1")).hasSize(3);
        assertThat(registry.connectionCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("超过单用户上限时断开最早的连接，保留最新的")
    void evictsOldestBeyondPerUserCap() {
        SocketClientService registry = service(2, 0);
        SocketIOClient first = mock(SocketIOClient.class);
        SocketIOClient second = mock(SocketIOClient.class);
        SocketIOClient third = mock(SocketIOClient.class);
        UUID firstId = UUID.randomUUID();

        registry.saveClient("u1", firstId, first);
        registry.saveClient("u1", UUID.randomUUID(), second);
        registry.saveClient("u1", UUID.randomUUID(), third);

        verify(first).disconnect();
        verify(third, never()).disconnect();
        // 被踢的那条必须同时从表里消失，否则推送还会往一个已断开的连接上发
        assertThat(registry.getUserClient("u1")).hasSize(2).doesNotContainKey(firstId);
        assertThat(registry.connectionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("总量超限时拒的是新连接，已经在看屏的人不受影响")
    void rejectsNewcomerBeyondGlobalCap() {
        SocketClientService registry = service(0, 2);
        SocketIOClient kept = mock(SocketIOClient.class);
        SocketIOClient refused = mock(SocketIOClient.class);

        assertThat(registry.saveClient("u1", UUID.randomUUID(), kept)).isTrue();
        assertThat(registry.saveClient("u2", UUID.randomUUID(), mock(SocketIOClient.class))).isTrue();
        assertThat(registry.saveClient("u3", UUID.randomUUID(), refused)).isFalse();

        // 拒新不踢旧：把在线的人挤掉会让故障表现成「所有人的屏都在闪」
        verify(kept, never()).disconnect();
        verify(refused, never()).disconnect();
        assertThat(registry.connectionCount()).isEqualTo(2);
        assertThat(registry.getAllClient()).hasSize(2);
    }

    @Test
    @DisplayName("断开事件重复投递不会把计数减成负数，空壳用户会被回收")
    void toleratesDuplicateDisconnectAndDropsEmptyShells() {
        SocketClientService registry = service(0, 0);
        UUID sessionId = UUID.randomUUID();
        registry.saveClient("u1", sessionId, mock(SocketIOClient.class));

        registry.deleteSessionClientByUserId("u1", sessionId);
        // @OnDisconnect 与「踢最老」都会摘同一条连接，第二次必须是幂等的
        registry.deleteSessionClientByUserId("u1", sessionId);
        registry.deleteSessionClientByUserId("ghost", UUID.randomUUID());

        assertThat(registry.connectionCount()).isZero();
        assertThat(registry.getAllClient()).isEmpty();
        assertThat(registry.getUserClient("u1")).isEmpty();
    }

    @Test
    @DisplayName("按用户整体下线：连接清空且计数回退")
    void dropsAllSessionsOfOneUser() {
        SocketClientService registry = service(0, 0);
        registry.saveClient("u1", UUID.randomUUID(), mock(SocketIOClient.class));
        registry.saveClient("u1", UUID.randomUUID(), mock(SocketIOClient.class));
        registry.saveClient("u2", UUID.randomUUID(), mock(SocketIOClient.class));

        registry.deleteUserCacheByUserId("u1");

        assertThat(registry.getUserClient("u1")).isEmpty();
        assertThat(registry.connectionCount()).isEqualTo(1);
        assertThat(registry.getAllClient()).hasSize(1);
    }
}
