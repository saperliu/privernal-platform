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

package com.privernal.job.config;

import com.privernal.job.executor.ScheduleExecutorPool;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 调度内核 Micrometer 埋点（P1 末项 / .B 报警二期）。
 *
 * <p>micrometer-core 已随 actuator 进 classpath（§2.2），注册指标零新增依赖；
 * exporter/采集落点（D5-C 二期：落到「任务报警列表」菜单口径）与
 * management.endpoints 暴露由运维确认——指标本身先就位，/actuator/metrics
 * 开启后即可被采集（schedule.* 前缀）。</p>
 *
 * <p>指标清单（§15）：
 * schedule.executions{outcome} 执行计数、schedule.misfires 错过计数、
 * schedule.pool.rejected 池拒绝计数、schedule.queue.enqueued 排队计数、
 * schedule.cas.round 扫描轮耗时（对应 §13 用例 14）、
 * schedule.pool.active/queue 执行池水位 gauge。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
public class ScheduleMetrics {

    private final MeterRegistry registry;

    private Counter executionsSuccess;
    private Counter executionsFailed;
    private Counter executionsTimeout;
    private Counter misfires;
    private Counter poolRejected;
    private Counter queueEnqueued;
    private Timer casRound;

    public ScheduleMetrics(ObjectProvider<MeterRegistry> registryProvider, ScheduleExecutorPool executorPool) {
        MeterRegistry meterRegistry = registryProvider.getIfAvailable();
        this.registry = meterRegistry;
        if (meterRegistry == null) {
            log.warn("[schedule] MeterRegistry 不存在（actuator 未引入？），Micrometer 埋点关闭");
            return;
        }
        executionsSuccess = Counter.builder("schedule.executions").tag("outcome", "success")
                .description("定时任务执行成功次数").register(meterRegistry);
        executionsFailed = Counter.builder("schedule.executions").tag("outcome", "failed")
                .description("定时任务执行失败次数").register(meterRegistry);
        executionsTimeout = Counter.builder("schedule.executions").tag("outcome", "timeout")
                .description("定时任务执行超时次数").register(meterRegistry);
        misfires = Counter.builder("schedule.misfires")
                .description("错过触发窗口次数").register(meterRegistry);
        poolRejected = Counter.builder("schedule.pool.rejected")
                .description("执行池/组织并发拒绝次数").register(meterRegistry);
        queueEnqueued = Counter.builder("schedule.queue.enqueued")
                .description("QUEUE 策略排队轮次数").register(meterRegistry);
        casRound = Timer.builder("schedule.cas.round")
                .description("单轮扫描+抢主+逐条CAS耗时（§13 用例 14 口径）").register(meterRegistry);
        executorPool.attachGauges(meterRegistry);
        log.info("[schedule] Micrometer metrics registered (schedule.*)");
    }

    public void execution(String outcome) {
        if (executionsSuccess == null) {
            return;
        }
        switch (outcome) {
            case "success" -> executionsSuccess.increment();
            case "timeout" -> executionsTimeout.increment();
            default -> executionsFailed.increment();
        }
    }

    public void misfire() {
        if (misfires != null) {
            misfires.increment();
        }
    }

    public void poolRejected() {
        if (poolRejected != null) {
            poolRejected.increment();
        }
    }

    public void queueEnqueued(long count) {
        if (queueEnqueued != null && count > 0) {
            queueEnqueued.increment(count);
        }
    }

    public void casRound(long millis) {
        if (casRound != null) {
            casRound.record(java.time.Duration.ofMillis(millis));
        }
    }

    public boolean enabled() {
        return registry != null;
    }

    /**
     * D5-C 二期方案 B：把当前指标状态采为快照行（SYSTEM 任务 metrics-snapshot 每分钟写库，
     * 报警列表消费 30 分钟窗口趋势）。计数器取累计值（窗口增量由查询侧差分），
     * Timer 取 COUNT/TOTAL_TIME/MAX，Gauge 取即时值。
     */
    public List<MetricSample> collectSnapshot() {
        List<MetricSample> rows = new ArrayList<>();
        if (registry == null) {
            return rows;
        }
        for (String outcome : List.of("success", "failed", "timeout")) {
            Counter counter = registry.find("schedule.executions").tag("outcome", outcome).counter();
            if (counter != null) {
                rows.add(new MetricSample("schedule.executions", outcome, "COUNT", counter.count()));
            }
        }
        addCounter(rows, "schedule.misfires");
        addCounter(rows, "schedule.pool.rejected");
        addCounter(rows, "schedule.queue.enqueued");
        Timer timer = registry.find("schedule.cas.round").timer();
        if (timer != null) {
            rows.add(new MetricSample("schedule.cas.round", null, "COUNT", timer.count()));
            rows.add(new MetricSample("schedule.cas.round", null, "TOTAL_TIME", timer.totalTime(java.util.concurrent.TimeUnit.SECONDS)));
            rows.add(new MetricSample("schedule.cas.round", null, "MAX", timer.max(java.util.concurrent.TimeUnit.SECONDS)));
        }
        addGauge(rows, "schedule.pool.active");
        addGauge(rows, "schedule.pool.queue");
        addGauge(rows, "schedule.pool.running");
        return rows;
    }

    private void addCounter(List<MetricSample> rows, String name) {
        Counter counter = registry.find(name).counter();
        if (counter != null) {
            rows.add(new MetricSample(name, null, "COUNT", counter.count()));
        }
    }

    private void addGauge(List<MetricSample> rows, String name) {
        io.micrometer.core.instrument.Gauge gauge = registry.find(name).gauge();
        if (gauge != null && !Double.isNaN(gauge.value())) {
            rows.add(new MetricSample(name, null, "VALUE", gauge.value()));
        }
    }

    /** 快照行 */
    public record MetricSample(String metricName, String tagOutcome, String statistic, double value) {
    }
}
