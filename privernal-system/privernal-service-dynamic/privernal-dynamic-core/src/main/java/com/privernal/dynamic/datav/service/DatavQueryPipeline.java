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

import java.util.function.Supplier;

/**
 * 大屏取数的保护链：并发闸门 + 按数据集熔断的**唯一**组合点。
 * <p>
 * 顺序是这套保护能不能用的关键，而它原本只活在调用点的一行注释里：闸门必须在外、熔断在内。
 * 反过来写成 {@code breaker.guard(() -> limiter.call(...))}，一次「排队排到超时」就会被算成
 * 该数据集的一次失败——突发流量下熔断器会把一个个本来健康的数据集依次跳闸，
 * 保护机制自己变成故障放大器。
 * <p>
 * 注释挡不住重构，所以把嵌套收进这一个方法：调用点只剩一次函数调用，顺序没有可写错的地方，
 * 并且这条不变式能被脱离 Spring 直接测（{@code DatavQueryPipelineTest}）。
 *
 * @author saperliu
 */
public final class DatavQueryPipeline {

    private DatavQueryPipeline() {
    }

    /**
     * 在闸门内、并在熔断器的看护下执行一次取数。
     *
     * @param limiter     并发闸门（关闭时直接执行，不碰信号量）
     * @param breaker     按数据集的熔断器（排队超时因此根本到不了它面前）
     * @param datasetCode 被保护的数据集编码，熔断状态按它 + 组织累计
     * @param query       真正打库的那段
     */
    public static <T> T execute(DatavQueryLimiter limiter, DatavQueryBreaker breaker,
                                String datasetCode, Supplier<T> query) {
        return limiter.call(() -> breaker.guard(datasetCode, query));
    }
}
