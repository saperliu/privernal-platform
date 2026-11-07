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

/**
 * 调度内核常量。
 *
 * @author saperliu
 */
public final class ScheduleConstants {

    private ScheduleConstants() {
    }

    // ===== 来源（§3.1）=====
    public static final String SOURCE_WORKFLOW = "WORKFLOW";
    public static final String SOURCE_DESIGNER = "DESIGNER";
    public static final String SOURCE_DESIGNER_DATA = "DESIGNER_DATA";
    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String SOURCE_OPENAPI = "OPENAPI";
    public static final String SOURCE_MANUAL = "MANUAL";

    /** P2-7：告警收件人扩展用的组织管理员角色标识（system_role.role_key） */
    public static final String ROLE_ORG_ADMIN = "SuperRole";

    // ===== 触发类型 =====
    public static final String TRIGGER_CRON = "CRON";
    public static final String TRIGGER_FIXED_DELAY = "FIXED_DELAY";
    public static final String TRIGGER_ONCE = "ONCE";

    // ===== 任务状态（§7.2 状态机）=====
    public static final String STATUS_READY = "READY";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_TRIGGERED = "TRIGGERED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    // ===== 日志状态（§6.2；SKIPPED_* 一律 attempt=0）=====
    public static final String LOG_RUNNING = "RUNNING";
    public static final String LOG_SUCCESS = "SUCCESS";
    public static final String LOG_FAILED = "FAILED";
    public static final String LOG_TIMEOUT = "TIMEOUT";
    public static final String LOG_SKIPPED_MISFIRE = "SKIPPED_MISFIRE";
    public static final String LOG_SKIPPED_CONCURRENT = "SKIPPED_CONCURRENT";
    public static final String LOG_SKIPPED_POOL = "SKIPPED_POOL";
    public static final String LOG_CANCELLED = "CANCELLED";

    // ===== 并发策略（§7.4，三档均已实现）=====
    public static final String CONCURRENT_FORBID = "FORBID";
    /** P1-7：与 FORBID 分家——被挡轮次直接丢弃并记 SKIPPED_CONCURRENT，相位从 now 推进 */
    public static final String CONCURRENT_SKIP = "SKIP";
    /** §7.4 P2：错过轮次落库排队逐轮补跑（队列表 system_schedule_task_queue，残留由 cleanupTick 清理） */
    public static final String CONCURRENT_QUEUE = "QUEUE";
    public static final String MISFIRE_COMPENSATE = "COMPENSATE";
    public static final String MISFIRE_SKIP = "SKIP";

    // ===== 扫描容量（§7.1；P2-1：扫描严格取已到期行，不再有预取窗口，故无 window 常量）=====
    public static final int SCAN_LIMIT = 2000;

    // ===== 租约与对账（§1.1/§7.3）=====
    public static final int TRIGGER_LEASE_SECONDS = 60;
    public static final int FAIL_PAUSE_THRESHOLD = 10;
    public static final long DAILY_PERIOD_SECONDS = 86400L;

    // ===== 失败重试退避（§7.7：1m / 5m / 30m）=====
    public static final long[] RETRY_BACKOFF_SECONDS = {60L, 300L, 1800L};

    // ===== 清理保留（§7.8）=====
    public static final int ONCE_RETAIN_DAYS = 7;
    /** 执行记录保留 1 个月：驱动归档、分区滚动与排队残留清理 */
    public static final int LOG_RETAIN_DAYS = 30;
    /** 指标快照/补偿事件只留 3 天，再早就失去了观测价值 */
    public static final int METRIC_RETAIN_DAYS = 3;
    public static final int OUTBOX_RETAIN_DAYS = 3;
}
