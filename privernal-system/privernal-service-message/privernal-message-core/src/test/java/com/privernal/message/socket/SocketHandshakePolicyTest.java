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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * socket 握手准入判据测试（纯函数，不起服务、不连 Redis）。
 * <p>
 * 这条判据决定「任何人能不能用假 token 占住服务端连接」，所以它必须能被反复跑，
 * 而不是等到上线时靠人工连一次看看。
 *
 * @author saperliu
 */
@DisplayName("socket 握手准入策略")
class SocketHandshakePolicyTest {

    @Test
    @DisplayName("参数不全一律拒绝，并且不去查 token 存储")
    void rejectsMissingParamsWithoutTouchingTokenStore() {
        AtomicInteger checks = new AtomicInteger();
        BiPredicate<String, String> check = (userId, token) -> {
            checks.incrementAndGet();
            return true;
        };

        assertThat(SocketHandshakePolicy.permits(null, "token-1", check)).isFalse();
        assertThat(SocketHandshakePolicy.permits("  ", "token-1", check)).isFalse();
        assertThat(SocketHandshakePolicy.permits("u1", null, check)).isFalse();
        assertThat(SocketHandshakePolicy.permits("u1", "   ", check)).isFalse();

        // 空 token 去打 sa-token 等于每次匿名握手都多一次 Redis 往返，那是给别人送放大器
        assertThat(checks).hasValue(0);
    }

    @Test
    @DisplayName("token 与 userId 对不上号 → 拒绝（拿别人的 userId + 自己的 token 连不上）")
    void rejectsMismatchedToken() {
        // 真实实现是 userId.equals(StpUtil.getLoginIdByToken(token))：token 属于谁，就只有谁能用它连上
        BiPredicate<String, String> tokenOwner = (userId, token) -> ("tok-" + userId).equals(token);

        assertThat(SocketHandshakePolicy.permits("u1", "tok-u1", tokenOwner)).isTrue();
        assertThat(SocketHandshakePolicy.permits("u2", "tok-u1", tokenOwner)).isFalse();
    }

    @Test
    @DisplayName("参数齐且校验通过 → 放行，且只查一次")
    void allowsValidPairWithSingleCheck() {
        AtomicInteger checks = new AtomicInteger();
        BiPredicate<String, String> check = (userId, token) -> {
            checks.incrementAndGet();
            return "u1".equals(userId) && "token-1".equals(token);
        };

        assertThat(SocketHandshakePolicy.permits("u1", "token-1", check)).isTrue();
        // checkTokenLogin 已经隐含「token 有效」与「账号在线」，这里多一次就是多一次 Redis
        assertThat(checks).hasValue(1);
    }
}
