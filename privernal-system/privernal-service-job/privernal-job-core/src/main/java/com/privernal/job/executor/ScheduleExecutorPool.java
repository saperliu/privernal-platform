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

package com.privernal.job.executor;

import com.privernal.job.kernel.ScheduleInstanceId;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 执行池与公平性隔离。
 *
 * <p>全局池 core 16 / max 64 / queue 10000 / AbortPolicy；每组织信号量默认 8——
 * 单组织打满只排队自己，不挤占其他组织。AbortPolicy 抛 RejectedExecutionException
 * 由投递方按 §8.2 分支回退（ONCE 顺延 30s / CRON 回 pending，不移动相位）。</p>
 *
 * <p>上下文隔离：任务 Runnable 内自行 set/clear（ScheduleContextRestorer.enter/finally close），
 * 池线程不依赖也不残留 TTL 状态（§9.3 落地注意4）。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
public class ScheduleExecutorPool {

    /** 每组织并发上限（§8.2 默认 8） */
    private static final int ORG_CONCURRENCY = 8;

    private final Map<String, Semaphore> orgSemaphores = new ConcurrentHashMap<>();

    /** 在跑 Future 注册表：终止运行时 Future.cancel(true) 尽力中断（§11） */
    private final Map<String, Future<?>> runningFutures = new ConcurrentHashMap<>();

    @Getter
    private ThreadPoolExecutor executor;

    private final ScheduleInstanceId instanceId;

    public ScheduleExecutorPool(ScheduleInstanceId instanceId) {
        this.instanceId = instanceId;
    }

    @PostConstruct
    public void init() {
        // P2-12：队列长度必须与 max 同量级，否则 ThreadPoolExecutor 的扩容语义失效——
        // 只有队列满才会把线程从 core(16) 涨到 max(64)，10000 的队列让 64 永不可达（实际恒 16 并发）。
        // 队列短了以后溢出走 AbortPolicy，由 §8.2 的投递方回退分支（ONCE 顺延/CRON 回 pending）兜底。
        executor = new ThreadPoolExecutor(
                16, 64, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                r -> {
                    Thread thread = new Thread(r, "schedule-exec-" + threadSeq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    private final java.util.concurrent.atomic.AtomicInteger threadSeq = new java.util.concurrent.atomic.AtomicInteger();

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * 投递执行；池满（或组织信号量不可立即获得时不等待，直接按拒绝处理）抛
     * RejectedExecutionException，由调用方走 §8.2 回退分支。
     *
     * <p>P2-12 说明：组织信号量在 submit 时占用、排队期间仍占额，是刻意保留的——
     * §8.2 的"回退不动相位"依赖投递线程内的**同步**拒绝；若把获取移到执行体内，
     * 行已 CAS 成 TRIGGERED 却没人回退，只能等租约过期由对账回收（RTO 90s），
     * 比"排队期间占一个组织名额"代价更高。代价上界是单组织同时在池内 8 个名额。</p>
     */
    public void submit(String orgId, String futureKey, Runnable action) {
        Semaphore semaphore = orgSemaphores.computeIfAbsent(orgId, k -> new Semaphore(ORG_CONCURRENCY));
        if (!semaphore.tryAcquire()) {
            throw new java.util.concurrent.RejectedExecutionException("org concurrency exhausted: " + orgId);
        }
        try {
            Future<?> future = executor.submit(() -> {
                try {
                    action.run();
                } finally {
                    semaphore.release();
                    runningFutures.remove(futureKey);
                }
            });
            runningFutures.put(futureKey, future);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            semaphore.release();
            throw e;
        }
    }

    /** 终止运行中的执行（尽力中断，§11） */
    public boolean cancel(String futureKey) {
        Future<?> future = runningFutures.get(futureKey);
        if (future != null) {
            future.cancel(true);
            runningFutures.remove(futureKey);
            return true;
        }
        return false;
    }

    public boolean isRunning(String futureKey) {
        Future<?> future = runningFutures.get(futureKey);
        return future != null && !future.isDone();
    }

    /** Micrometer gauge 注册（§15 埋点：执行池水位） */
    public void attachGauges(io.micrometer.core.instrument.MeterRegistry registry) {
        io.micrometer.core.instrument.Gauge.builder("schedule.pool.active", executor,
                e -> e.getActiveCount()).description("执行池活跃线程数").register(registry);
        io.micrometer.core.instrument.Gauge.builder("schedule.pool.queue", executor,
                e -> e.getQueue().size()).description("执行池队列积压").register(registry);
        io.micrometer.core.instrument.Gauge.builder("schedule.pool.running", runningFutures,
                f -> f.size()).description("在跑任务数").register(registry);
    }

    /** 实例心跳观测数据（§6.4 stat_json） */
    public String statJson() {
        return String.format("{\"active\":%d,\"poolSize\":%d,\"queue\":%d,\"running\":%d,\"orgs\":%d}",
                executor.getActiveCount(), executor.getPoolSize(), executor.getQueue().size(),
                runningFutures.size(), orgSemaphores.size());
    }

    public String instance() {
        return instanceId.getInstanceId();
    }
}
