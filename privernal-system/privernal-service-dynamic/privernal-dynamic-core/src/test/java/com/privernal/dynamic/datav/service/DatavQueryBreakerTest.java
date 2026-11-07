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
 * 数据集熔断器测试（M6.6）：纯 JUnit + AssertJ，不起 Spring、不连库。
 * <p>
 * 时钟与身份都通过包级缝隙注入假实现，冷却窗口靠拨动假时钟验证，而不是靠 sleep——
 * 用真实时间验证 30s 冷却既慢又不可靠。
 *
 * @author saperliu
 */
@DisplayName("大屏数据集熔断器测试")
class DatavQueryBreakerTest {

    /** 假时钟 + 固定身份作用域，让状态机完全可确定地推进 */
    private static final class TestBreaker extends DatavQueryBreaker {
        long now = 1_000L;

        TestBreaker(boolean enabled, int threshold, long cooldownMillis) {
            this.enabled = enabled;
            this.failureThreshold = threshold;
            this.cooldownMillis = cooldownMillis;
        }

        @Override
        long nowMillis() {
            return now;
        }

        @Override
        String identityScope() {
            return "org-1";
        }

        void advance(long millis) {
            now += millis;
        }
    }

    private static final class Boom extends RuntimeException {
        Boom() {
            super("源站挂了");
        }
    }

    private static TestBreaker breaker() {
        return new TestBreaker(true, 3, 30_000L);
    }

    @Test
    @DisplayName("连续失败达到阈值后打开，后续调用被快速拒绝且不再打库")
    void opensAfterThresholdFailures() {
        TestBreaker breaker = breaker();
        AtomicInteger executions = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
                executions.incrementAndGet();
                throw new Boom();
            })).isInstanceOf(Boom.class);
        }
        assertThat(executions).hasValue(3);

        // 第 4 次：熔断已开，直接抛「熔断」而不是再次执行查询
        assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
            executions.incrementAndGet();
            return "不应执行";
        })).isInstanceOf(ServiceException.class).hasMessageContaining("熔断");
        assertThat(executions).hasValue(3);
    }

    @Test
    @DisplayName("失败次数不足阈值时不打开；中途一次成功会清零计数")
    void doesNotOpenBelowThreshold() {
        TestBreaker breaker = breaker();
        AtomicInteger executions = new AtomicInteger();

        // 两次失败（< 3），不跳闸
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
                executions.incrementAndGet();
                throw new Boom();
            })).isInstanceOf(Boom.class);
        }
        // 一次成功，计数清零
        assertThat(breaker.guard("ds_a", () -> {
            executions.incrementAndGet();
            return "ok";
        })).isEqualTo("ok");
        // 之后再来两次失败仍不该跳闸（因为成功已把计数复位）
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
                executions.incrementAndGet();
                throw new Boom();
            })).isInstanceOf(Boom.class);
        }
        assertThat(executions).hasValue(5);
    }

    @Test
    @DisplayName("冷却到点后进入半开，且同一时刻只放行一个探测")
    void cooldownOpensHalfOpenWithSingleProbe() {
        TestBreaker breaker = breaker();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
                throw new Boom();
            })).isInstanceOf(Boom.class);
        }
        // 冷却未到：仍然快速拒绝
        breaker.advance(29_000L);
        assertThatThrownBy(() -> breaker.guard("ds_a", () -> "不应执行"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("熔断");

        // 冷却已到：探测在飞期间，第二个并发调用必须被拒
        breaker.advance(1_000L);
        AtomicInteger inner = new AtomicInteger();
        assertThat(breaker.guard("ds_a", () -> {
            // 模拟探测未返回时另一线程同数据集的请求
            assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
                inner.incrementAndGet();
                return "并发探测不该被执行";
            })).isInstanceOf(ServiceException.class);
            return "probe";
        })).isEqualTo("probe");
        // 探测成功 → 闭合
        assertThat(inner).hasValue(0);
    }

    @Test
    @DisplayName("半开探测成功后电路闭合，需再攒满阈值才重新打开")
    void probeSuccessCloses() {
        TestBreaker breaker = breaker();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
                throw new Boom();
            })).isInstanceOf(Boom.class);
        }
        breaker.advance(30_000L);
        // 探测成功 → 闭合
        assertThat(breaker.guard("ds_a", () -> "ok")).isEqualTo("ok");

        // 闭合后失败计数从 0 重新起算：2 次失败仍放行
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
                throw new Boom();
            })).isInstanceOf(Boom.class);
        }
        assertThat(breaker.guard("ds_a", () -> "still-run")).isEqualTo("still-run");
    }

    @Test
    @DisplayName("半开探测失败立即重新打开并重新计时")
    void probeFailureReopens() {
        TestBreaker breaker = breaker();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
                throw new Boom();
            })).isInstanceOf(Boom.class);
        }
        breaker.advance(30_000L);
        // 这次探测失败：应重新打开，且 openedAt 重置为当下
        assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
            throw new Boom();
        })).isInstanceOf(Boom.class);

        // 刚重新打开，未到新的冷却窗口 → 快速拒绝
        breaker.advance(10_000L);
        assertThatThrownBy(() -> breaker.guard("ds_a", () -> "不应执行"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("熔断");
    }

    @Test
    @DisplayName("一个组织的熔断不影响另一个组织：作用域按组织隔离")
    void circuitsAreIsolatedPerOrganization() {
        TestBreaker breaker = breaker();
        // org-A 打满阈值，熔断开路
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> breaker.guard("ds_a", "org-A", () -> {
                throw new Boom();
            })).isInstanceOf(Boom.class);
        }
        assertThatThrownBy(() -> breaker.guard("ds_a", "org-A", () -> "不应执行"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("熔断");

        // 同一数据集、另一组织：正常执行，不受 org-A 影响
        AtomicInteger ran = new AtomicInteger();
        assertThat(breaker.guard("ds_a", "org-B", () -> {
            ran.incrementAndGet();
            return "ok";
        })).isEqualTo("ok");
        assertThat(ran).hasValue(1);
    }

    @Test
    @DisplayName("默认关闭：不做任何熔断，失败全部照常打库")
    void disabledNeverBreaks() {
        TestBreaker breaker = new TestBreaker(false, 3, 30_000L);
        AtomicInteger executions = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> breaker.guard("ds_a", () -> {
                executions.incrementAndGet();
                throw new Boom();
            })).isInstanceOf(Boom.class);
        }
        assertThat(executions).hasValue(10);
    }
}
