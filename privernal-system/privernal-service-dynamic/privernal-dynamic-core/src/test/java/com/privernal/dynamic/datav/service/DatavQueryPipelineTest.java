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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 保护链顺序测试（M6.6）
 * <p>
 * 这里只钉一件事：**排队超时不能被算成数据集的一次失败**。反过来的嵌套（熔断在外）在
 * 功能测试里完全正常——查询照样被限流、照样被熔断——只有在真实突发下才露出真面目：
 * 排队超时累积成失败，健康的数据集被逐个跳闸，保护变成故障放大器。
 * 那种行为没人会去手测，所以只能靠这里。
 *
 * @author saperliu
 */
@DisplayName("大屏取数保护链顺序测试")
class DatavQueryPipelineTest {

    /** 记录 guard 有没有被真正调用过，用来证明排队超时的请求根本没走到熔断器面前 */
    private static final class RecordingBreaker extends DatavQueryBreaker {

        final AtomicInteger seen = new AtomicInteger();

        RecordingBreaker(boolean enabled, int threshold, long cooldownMillis) {
            this.enabled = enabled;
            this.failureThreshold = threshold;
            this.cooldownMillis = cooldownMillis;
        }

        @Override
        public <T> T guard(String datasetCode, Supplier<T> query) {
            seen.incrementAndGet();
            return super.guard(datasetCode, query);
        }
    }

    private static DatavQueryLimiter limiter(int maxConcurrent, long timeoutMillis) {
        DatavQueryLimiter limiter = new DatavQueryLimiter();
        limiter.maxConcurrent = maxConcurrent;
        limiter.acquireTimeoutMillis = timeoutMillis;
        limiter.init();
        return limiter;
    }

    private static final class Boom extends RuntimeException {

        Boom() {
            super("源站挂了");
        }
    }

    @Test
    @DisplayName("闸门已满时直接拒绝，熔断器一次都没被调用")
    void queueTimeoutNeverReachesBreaker() throws Exception {
        DatavQueryLimiter limiter = limiter(1, 40L);
        RecordingBreaker breaker = new RecordingBreaker(true, 1, 30_000L);

        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread occupier = new Thread(() -> limiter.call(() -> {
            holding.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "held";
        }));
        occupier.setDaemon(true);
        occupier.start();
        assertThat(holding.await(2, TimeUnit.SECONDS)).as("占住唯一许可").isTrue();

        // 阈值设成 1：只要熔断器被调用并且抛错，它就会跳闸。这里要的是它压根没被调用。
        assertThatThrownBy(() -> DatavQueryPipeline.execute(limiter, breaker, "ds_a", () -> "never ran"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("并发已满");
        assertThat(breaker.seen).as("排队超时的请求不该出现在熔断器面前").hasValue(0);

        release.countDown();
        occupier.join(2000);
    }

    @Test
    @DisplayName("真打库的失败照样被熔断器记到（嵌套没把可见性弄丢）")
    void realFailureIsStillCounted() {
        DatavQueryLimiter limiter = limiter(0, 2000L);
        RecordingBreaker breaker = new RecordingBreaker(true, 1, 30_000L);

        assertThatThrownBy(() -> DatavQueryPipeline.execute(limiter, breaker, "ds_b", () -> {
            throw new Boom();
        })).isInstanceOf(Boom.class);
        assertThat(breaker.seen).hasValue(1);

        // 阈值 1，上一次真失败就该跳闸
        assertThatThrownBy(() -> DatavQueryPipeline.execute(limiter, breaker, "ds_b", () -> "ok"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("熔断");
    }

    @Test
    @DisplayName("闸门关闭时不做任何绕过，熔断器仍看得到每一次取数")
    void disabledLimiterDoesNotBypassBreaker() {
        DatavQueryLimiter limiter = limiter(0, 2000L);
        RecordingBreaker breaker = new RecordingBreaker(true, 3, 30_000L);

        assertThat(DatavQueryPipeline.execute(limiter, breaker, "ds_c", () -> "value")).isEqualTo("value");
        assertThat(breaker.seen).hasValue(1);
    }
}
