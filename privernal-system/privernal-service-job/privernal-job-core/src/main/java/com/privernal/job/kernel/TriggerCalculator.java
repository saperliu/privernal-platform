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
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 触发时间计算器。
 *
 * <p>jitter 三条口径的落点：① 偏移计入 next_fire_time（时间轮/执行链不再等待）；
 * ② 确定性 hash——同一任务每轮偏移一致（预览稳定、相位不游走）；
 * ③ FIXED_DELAY 禁配 jitter，注册时随机化首次相位（hash(taskId) % interval），
 * 之后严格按固定间隔推进。全平台按数据库服务器时区计算（不设 time_zone 列，注3）。</p>
 *
 * @author saperliu
 */
@Component
public class TriggerCalculator {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    /** 解析并校验 6 位 cron；非法抛 IllegalArgumentException */
    public CronExpression parseCron(String cron) {
        if (!CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("cron 表达式非法: " + cron);
        }
        return CronExpression.parse(cron);
    }

    /** cron 理论下次时刻（不含 jitter）。cron 为 null/空时返回 null（防御数据完整性异常） */
    public Date nextCronTheory(String cron, Date from) {
        if (cron == null || cron.isEmpty()) {
            return null;
        }
        LocalDateTime next = parseCron(cron).next(LocalDateTime.ofInstant(from.toInstant(), ZONE));
        return next == null ? null : Date.from(next.atZone(ZONE).toInstant());
    }

    /** 周期长度：连续两次触发差值（§6.1 period_seconds 口径），供失败分档判定 */
    public Long computePeriodSeconds(String cron) {
        Date now = new Date();
        Date first = nextCronTheory(cron, now);
        if (first == null) {
            return null;
        }
        Date second = nextCronTheory(cron, first);
        if (second == null) {
            return null;
        }
        return (second.getTime() - first.getTime()) / 1000L;
    }

    /** 确定性 jitter 偏移秒数：hash(taskId) % jitterSeconds，同一任务每轮一致（§7.1 口径2） */
    public int jitterOffset(String taskId, int jitterSeconds) {
        if (jitterSeconds <= 0) {
            return 0;
        }
        return (taskId.hashCode() & 0x7fffffff) % jitterSeconds;
    }

    /**
     * 由"含 jitter 的当前触发时刻"推算周期任务下一次触发时刻（保持同一确定性偏移）。
     * 基准理论时刻 = 当前时刻 - 本任务 jitter 偏移，再取 cron next 后重新叠加偏移。
     */
    public Date nextCycleFireTime(SystemScheduleTask task, Date currentFireTime) {
        if (ScheduleConstants.TRIGGER_FIXED_DELAY.equals(task.getTriggerType())) {
            long interval = (task.getIntervalSeconds() == null ? 60 : task.getIntervalSeconds()) * 1000L;
            // 严格按固定间隔推进；currentFireTime 为空（首注册）时由 buildInitialNext 处理
            return new Date((currentFireTime == null ? System.currentTimeMillis() : currentFireTime.getTime()) + interval);
        }
        int offset = jitterOffset(task.getId(), task.getJitterSeconds() == null ? 0 : task.getJitterSeconds());
        Date base = new Date(currentFireTime.getTime() - offset * 1000L);
        Date theory = nextCronTheory(task.getCronExpr(), base);
        if (theory == null) {
            return null;
        }
        return new Date(theory.getTime() + offset * 1000L);
    }

    /**
     * 注册/启用时计算首触发时间：
     * CRON —— 下个理论时刻 + jitter；
     * FIXED_DELAY —— now + interval - hash%interval（首次相位随机化，之后等间隔，§7.1 口径3）；
     */
    public Date buildInitialNext(SystemScheduleTask task) {
        if (ScheduleConstants.TRIGGER_CRON.equals(task.getTriggerType())) {
            Date theory = nextCronTheory(task.getCronExpr(), new Date());
            if (theory == null) {
                return null;
            }
            int offset = jitterOffset(task.getId(), task.getJitterSeconds() == null ? 0 : task.getJitterSeconds());
            return new Date(theory.getTime() + offset * 1000L);
        }
        if (ScheduleConstants.TRIGGER_FIXED_DELAY.equals(task.getTriggerType())) {
            int interval = task.getIntervalSeconds() == null ? 60 : task.getIntervalSeconds();
            int hashOffset = (task.getId().hashCode() & 0x7fffffff) % interval;
            return new Date(System.currentTimeMillis() + (interval - hashOffset) * 1000L);
        }
        return task.getNextFireTime();
    }

    /** misfire 窗口上限：min(misfire_window, 周期长度)（§7.7；周期任务不得超过一个周期） */
    public long misfireWindowMs(SystemScheduleTask task) {
        int window = task.getMisfireWindowSeconds() == null ? 300 : task.getMisfireWindowSeconds();
        if (task.getPeriodSeconds() != null && task.getPeriodSeconds() > 0) {
            window = (int) Math.min(window, task.getPeriodSeconds());
        }
        return window * 1000L;
    }

    /** 未来 n 次触发预览（§12.2 previewNext：唯一不带 orgId 的方法，jitter 确定性保证结果稳定） */
    public List<Date> previewNext(String cron, int n) {
        List<Date> result = new ArrayList<>();
        Date cursor = new Date();
        for (int i = 0; i < n; i++) {
            cursor = nextCronTheory(cron, cursor);
            if (cursor == null) {
                break;
            }
            result.add(cursor);
        }
        return result;
    }

    /** ONCE 失败重试退避（§7.7：1m / 5m / 30m，共至多 4 次执行） */
    public long retryBackoffSeconds(int previousAttempt) {
        int idx = Math.min(Math.max(previousAttempt - 1, 0), ScheduleConstants.RETRY_BACKOFF_SECONDS.length - 1);
        return ScheduleConstants.RETRY_BACKOFF_SECONDS[idx];
    }
}