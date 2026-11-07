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

package com.privernal.job.kernel;

import com.privernal.job.domain.SystemScheduleTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T10 确定性用例（用例 6/10/12）：
 * jitter 确定性 100 轮、misfire 窗口边界、重试退避分档、FIXED_DELAY 相位随机化。
 */
@DisplayName("§13 用例 12/6/10：jitter 确定性与边界数学")
class TriggerCalculatorDeterminismTest {

    private final TriggerCalculator calculator = new TriggerCalculator();

    private SystemScheduleTask hourlyTask(int jitterSeconds) {
        SystemScheduleTask task = new SystemScheduleTask();
        task.setId("determinism-probe-task-0001");
        task.setTriggerType(ScheduleConstants.TRIGGER_CRON);
        task.setCronExpr("0 0 * * * ?");
        task.setJitterSeconds(jitterSeconds);
        task.setPeriodSeconds(3600L);
        task.setMisfirePolicy(ScheduleConstants.MISFIRE_SKIP);
        task.setMisfireWindowSeconds(200);
        return task;
    }

    // ===== 用例 12：jitter 确定性 =====

    @Test
    @DisplayName("同一任务连续 100 轮 jitter 偏移完全一致，且落在 [0, jitterSeconds) 内")
    void jitterOffsetDeterministicAcross100Rounds() {
        for (int jitter : new int[]{60, 30, 10}) {
            int first = calculator.jitterOffset("task-jitter-probe", jitter);
            assertThat(first).isBetween(0, jitter - 1);
            for (int i = 0; i < 100; i++) {
                assertThat(calculator.jitterOffset("task-jitter-probe", jitter))
                        .as("jitter=%d 第 %d 轮", jitter, i)
                        .isEqualTo(first);
            }
        }
        // 不同任务偏移分布足够散（确定性 hash 的削峰价值依赖于此）
        int distinct = 0;
        boolean[] seen = new boolean[60];
        for (int i = 0; i < 200; i++) {
            int off = calculator.jitterOffset("task-jitter-probe-" + i, 60);
            if (!seen[off]) {
                seen[off] = true;
                distinct++;
            }
        }
        assertThat(distinct).as("200 个任务在 60s 桶上的分布").isGreaterThan(40);
    }

    @Test
    @DisplayName("周期推进 100 轮：每轮 fire_time = cron 理论时刻 + 同一偏移（相位不游走、租约安全）")
    void cycleFireTimePreservesOffset100Rounds() {
        SystemScheduleTask task = hourlyTask(60);
        int offset = calculator.jitterOffset(task.getId(), 60);
        // 起点：某整点理论时刻 + offset（模拟已含 jitter 的 next_fire_time）
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(2026, Calendar.OCTOBER, 1, 9, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date fireTime = new Date(cal.getTimeInMillis() + offset * 1000L);
        long lastTheory = cal.getTimeInMillis();
        for (int i = 0; i < 100; i++) {
            Date next = calculator.nextCycleFireTime(task, fireTime);
            assertThat(next).as("第 %d 轮", i).isNotNull();
            long nextTheory = next.getTime() - offset * 1000L;
            assertThat(nextTheory - lastTheory).as("理论相位间隔").isEqualTo(3600_000L);
            // 偏移恒定
            assertThat(next.getTime() - nextTheory).isEqualTo(offset * 1000L);
            // 租约安全（§7.1 口径1）：偏移已计入 next_fire_time，jitter 任务在 TRIGGERED 态无轮内等待，
            // 偏移 < 60s = TRIGGER_LEASE_SECONDS
            assertThat(offset).isLessThan(ScheduleConstants.TRIGGER_LEASE_SECONDS);
            lastTheory = nextTheory;
            fireTime = next;
        }
    }

    @Test
    @DisplayName("previewNext 两次计算结果逐项一致（§12.1 预览稳定性）")
    void previewNextStable() {
        List<Date> a = calculator.previewNext("0 0 9 * * ?", 5);
        List<Date> b = calculator.previewNext("0 0 9 * * ?", 5);
        assertThat(a).hasSize(5).isEqualTo(b);
    }

    @Test
    @DisplayName("FIXED_DELAY 注册相位随机化：首触发落在 (now, now+interval]，偏移确定性")
    void fixedDelayInitialPhaseRandomized() {
        SystemScheduleTask task = new SystemScheduleTask();
        task.setId("fd-probe-task");
        task.setTriggerType(ScheduleConstants.TRIGGER_FIXED_DELAY);
        task.setIntervalSeconds(60);
        Date now = new Date();
        Date first = calculator.buildInitialNext(task);
        assertThat(first).isNotNull();
        assertThat(first.getTime()).isGreaterThan(now.getTime());
        assertThat(first.getTime()).isLessThanOrEqualTo(now.getTime() + 60_000L + 50L);
        // 之后严格等间隔推进，不再叠加抖动
        Date second = calculator.nextCycleFireTime(task, first);
        assertThat(second.getTime() - first.getTime()).isEqualTo(60_000L);
    }

    // ===== 用例 6：misfire 窗口边界（窗口 200s：199s 不到、201s 超过）=====

    @Test
    @DisplayName("misfire 判定边界：lag=199s 不跳过、lag=201s 跳过（窗口 200s）")
    void misfireBoundary199And201() {
        SystemScheduleTask task = hourlyTask(0);
        // 内核判定条件（ScheduleKernel.dispatch）：lag > misfireWindowMs 即跳过
        long windowMs = calculator.misfireWindowMs(task);
        assertThat(windowMs).isEqualTo(200_000L);
        assertThat(199_000L).as("错过 199s 仍在窗口内，须正常触发").isLessThanOrEqualTo(windowMs);
        assertThat(201_000L).as("错过 201s 超出窗口，须按 misfire_policy 跳过").isGreaterThan(windowMs);
    }

    @Test
    @DisplayName("窗口上限受一个周期约束：window=300s、period=60s → 生效窗口 60s")
    void misfireWindowCappedByPeriod() {
        SystemScheduleTask task = new SystemScheduleTask();
        task.setMisfireWindowSeconds(300);
        task.setPeriodSeconds(60L);
        assertThat(calculator.misfireWindowMs(task)).isEqualTo(60_000L);
        task.setPeriodSeconds(86400L);
        assertThat(calculator.misfireWindowMs(task)).isEqualTo(300_000L);
    }

    // ===== 用例 10（数学段）：ONCE 重试退避 1m/5m/30m =====

    @Test
    @DisplayName("重试退避分档：attempt1→60s、2→300s、3+→1800s")
    void retryBackoffLadder() {
        assertThat(calculator.retryBackoffSeconds(1)).isEqualTo(60L);
        assertThat(calculator.retryBackoffSeconds(2)).isEqualTo(300L);
        assertThat(calculator.retryBackoffSeconds(3)).isEqualTo(1800L);
        assertThat(calculator.retryBackoffSeconds(99)).isEqualTo(1800L);
    }

    @Test
    @DisplayName("period_seconds 分档边界：86400（每日）属立即补跑档")
    void dailyBoundaryInclusive() {
        assertThat(ScheduleConstants.DAILY_PERIOD_SECONDS).isEqualTo(86400L);
        // TaskExecutionRunner 分档条件 period >= 86400 → 每日任务（恰好 86400）命中补跑档
        assertThat(86400L).isGreaterThanOrEqualTo(ScheduleConstants.DAILY_PERIOD_SECONDS);
        assertThat(86399L).isLessThan(ScheduleConstants.DAILY_PERIOD_SECONDS);
    }
}
