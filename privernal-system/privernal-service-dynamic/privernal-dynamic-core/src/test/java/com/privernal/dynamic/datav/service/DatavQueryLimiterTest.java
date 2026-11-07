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

package com.privernal.dynamic.datav.service;

import com.privernal.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 大屏取数并发闸门测试（M6.6）：纯 JUnit + AssertJ，不起 Spring。
 * <p>
 * 最要紧的不变式是「异常也要归还许可」——泄漏许可会慢慢把闸门焊死，那比没限流更糟，所以单独覆盖。
 * 排队超时用预先抽干许可 + 极短超时验证，避免依赖真实等待与线程调度。
 *
 * @author saperliu
 */
@DisplayName("大屏取数并发闸门测试")
class DatavQueryLimiterTest {

    private static final class Boom extends RuntimeException {
        Boom() {
            super("源站挂了");
        }
    }

    /** 手动装配 + 触发 @PostConstruct，避免为了单测拉起 Spring */
    private static DatavQueryLimiter limiter(int maxConcurrent, long timeoutMillis) {
        DatavQueryLimiter limiter = new DatavQueryLimiter();
        limiter.maxConcurrent = maxConcurrent;
        limiter.acquireTimeoutMillis = timeoutMillis;
        limiter.init();
        return limiter;
    }

    @Test
    @DisplayName("默认不限：max=0 时直接执行且不碰信号量")
    void disabledRunsDirectly() {
        DatavQueryLimiter limiter = limiter(0, 2000L);
        assertThat(limiter.enabled()).isFalse();
        // 关闭时 semaphore 为 null；call 必须绕开它，否则 NPE
        assertThat(limiter.call(() -> "ok")).isEqualTo("ok");

        AtomicInteger ran = new AtomicInteger();
        assertThatThrownBy(() -> limiter.call(() -> {
            ran.incrementAndGet();
            throw new Boom();
        })).isInstanceOf(Boom.class);
        assertThat(ran).hasValue(1);
    }

    @Test
    @DisplayName("成功路径归还许可，闸门恢复满额")
    void releasesPermitOnSuccess() {
        DatavQueryLimiter limiter = limiter(2, 1000L);
        assertThat(limiter.semaphore.availablePermits()).isEqualTo(2);
        assertThat(limiter.call(() -> "ok")).isEqualTo("ok");
        assertThat(limiter.semaphore.availablePermits()).isEqualTo(2);
    }

    @Test
    @DisplayName("查询抛异常也归还许可：绝不泄漏")
    void releasesPermitOnException() {
        DatavQueryLimiter limiter = limiter(1, 1000L);
        assertThatThrownBy(() -> limiter.call(() -> {
            throw new Boom();
        })).isInstanceOf(Boom.class);
        // 泄漏一份许可就会永久少一份并发，这里必须回到初始满额
        assertThat(limiter.semaphore.availablePermits()).isEqualTo(1);
        // 且闸门仍可正常使用（证明确实没被焊死）
        assertThat(limiter.call(() -> "again")).isEqualTo("again");
    }

    @Test
    @DisplayName("并发已满且排队超时时，返回「下一轮刷新再试」而不是数据错误")
    void timeoutReportsRetryNextRefresh() {
        DatavQueryLimiter limiter = limiter(1, 30L);
        // 预先把唯一许可抽干，模拟闸门已满
        limiter.semaphore.acquireUninterruptibly();

        AtomicInteger ran = new AtomicInteger();
        assertThatThrownBy(() -> limiter.call(() -> {
            ran.incrementAndGet();
            return "不应执行";
        })).isInstanceOf(ServiceException.class)
                .hasMessageContaining("下一次自动刷新");
        // 超时时查询根本没跑
        assertThat(ran).hasValue(0);

        // 归还后应能继续服务，证明这条失败路径没顺带泄漏许可
        limiter.semaphore.release();
        assertThat(limiter.call(() -> "ok")).isEqualTo("ok");
    }
}
