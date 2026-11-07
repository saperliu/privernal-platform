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

import cn.hutool.json.JSONObject;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.job.context.ScheduleContextRestorer;
import com.privernal.job.domain.SystemScheduleInstance;
import com.privernal.job.domain.SystemScheduleTask;
import com.privernal.job.domain.SystemScheduleTaskLog;
import com.privernal.job.executor.ScheduleExecutorPool;
import com.privernal.job.mapper.ScheduleLogMapper;
import com.privernal.job.mapper.ScheduleTaskCoreMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 调度内核：DB 租约选主→ 每秒扫描 + 逐条 CAS
 * → 延迟投递执行池（时间轮的 per-task 等价实现：jitter 已计入 next_fire_time，
 * 轮内只剩 ≤2s 微延迟，单实例下无内存压力收益，不引入 4 槽数组）→ 对账（§7.5/§7.7）
 * → 清理（§7.8）→ 心跳（§6.4）。
 *
 * <p>内核线程一律先做线程级数据权限豁免再触库、finally 成对清理（§9.3 三件套的
 * 第①件；②③在 Mapper 类注解与 IGNORE_TABLES）。自建调度线程池（poolSize=4，
 * 前缀 schedule-），不与业务 cron 抢 Boot 默认调度线程（§7.1）。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
public class ScheduleKernel {

    @Autowired
    private ScheduleTaskCoreMapper coreMapper;

    @Autowired
    private ScheduleLogMapper logMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource dataSource;

    /** null=未探测；PARTITIONS/ALTER PARTITION 是 MySQL 专属，SQLite 模式整体跳过分区维护 */
    private volatile Boolean mysqlFamily;

    @Autowired
    private ScheduleExecutorPool executorPool;

    @Autowired
    private TaskExecutionRunner runner;

    @Autowired
    private TriggerCalculator calculator;

    @Autowired
    private ScheduleContextRestorer contextRestorer;

    @Autowired
    private ScheduleInstanceId instanceId;

    @Autowired
    private com.privernal.job.config.ScheduleMetrics metrics;

    @Autowired
    private PendingChangeApplier pendingApplier;

    private ScheduledThreadPoolExecutor scheduler;

    @PostConstruct
    public void start() {
        scheduler = new ScheduledThreadPoolExecutor(4, r -> {
            Thread thread = new Thread(r, "schedule-kernel");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(TaskUtils.decorateTaskWithErrorHandler(this::scanTick, this::onError, true),
                3, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(TaskUtils.decorateTaskWithErrorHandler(this::heartbeatTick, this::onError, true),
                5, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(TaskUtils.decorateTaskWithErrorHandler(this::reconcileTick, this::onError, true),
                15, 30, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(TaskUtils.decorateTaskWithErrorHandler(this::cleanupTick, this::onError, true),
                60, TimeUnit.HOURS.toSeconds(6), TimeUnit.SECONDS);
        // P1-3：启动期分区断言（延后 20s 等数据源就绪，一次性）。分区滚动原本只挂在 6h 的
        // cleanupTick 上，新装库若缺可用分区，头 6 小时的日志插入会失败并被幂等分支误读为
        // "本轮已被别人执行"→ 任务静默消失；这里先补建、再把仍然不够的缺口显式 ERROR 告警。
        scheduler.schedule(TaskUtils.decorateTaskWithErrorHandler(this::startupPartitionCheck, this::onError, true),
                20, TimeUnit.SECONDS);
        log.info("[schedule] kernel started, instance={}", instanceId.getInstanceId());
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        // 优雅停机注销本实例：否则实例表会留下 role 停留在 LEADER 的行（租约 30s 就过期，
        // 但清理 tick 每 6h 才跑一次、滞留阈值 300 分钟），实例状态页长时间显示两个 LEADER
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            logMapper.deleteInstance(instanceId.getInstanceId());
            log.info("[schedule] instance deregistered, instance={}", instanceId.getInstanceId());
        } catch (Exception e) {
            log.warn("[schedule] instance deregister failed (lease 过期后由清理回收): {}", e.getMessage());
        }
    }

    private void onError(Throwable t) {
        log.error("[schedule] kernel task error", t);
    }

    /** P1-4：行从在途状态落回时应用登记的变更；失败不影响内核主流程，下一轮回写或对账重试 */
    private void applyPendingQuietly(String taskId) {
        try {
            pendingApplier.apply(taskId);
        } catch (Exception e) {
            log.warn("[schedule] 在途变更应用失败 taskId={}", taskId, e);
        }
    }

    // ================= 每秒：抢主 → 扫描 → misfire → CAS → 投递 =================

    private void scanTick() {
        long tickStart = System.currentTimeMillis();
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            // 抢主兼续租：holder 是本实例或租约过期时 affected=1（§7.3；单实例常态即本实例）
            if (coreMapper.tryAcquireSchedulerLock(instanceId.getInstanceId()) == 0) {
                return;
            }
            List<SystemScheduleTask> rows = coreMapper.scanReady(ScheduleConstants.SCAN_LIMIT);
            // §15 P0 告警：扫描恒 0 行但库里存在已到期 READY 行——数据权限改写(1=2)/锁行缺失
            // 两类"静默全瘫"的唯一可观测信号；限频每 15 轮（约 15s）记一次，避免刷屏
            if (rows.isEmpty()) {
                long due = coreMapper.countReadyDue();
                if (due > 0) {
                    zeroScanStreak++;
                    if (zeroScanStreak % 15 == 1) {
                        log.error("[schedule] 静默全瘫预警：扫描返回 0 行但存在 {} 条已到期 READY 行"
                                + "（检查数据权限豁免/scheduler 锁行/调度主状态），连续 {} 轮", due, zeroScanStreak);
                    }
                } else {
                    zeroScanStreak = 0;
                }
            } else {
                zeroScanStreak = 0;
            }
            for (SystemScheduleTask task : rows) {
                try {
                    dispatch(task);
                } catch (Exception e) {
                    log.error("[schedule] dispatch failed taskId={}", task.getId(), e);
                }
            }
            metrics.casRound(System.currentTimeMillis() - tickStart);
        }
    }

    /** 静默全瘫预警的连续零行轮次计数（scanTick 内单线程访问） */
    private long zeroScanStreak;

    private void dispatch(SystemScheduleTask task) {
        Date now = new Date();
        Date next = task.getNextFireTime();
        if (next == null) {
            log.warn("[schedule] READY row without next_fire_time, skip taskId={}", task.getId());
            return;
        }
        boolean once = ScheduleConstants.TRIGGER_ONCE.equals(task.getTriggerType());
        boolean cronType = ScheduleConstants.TRIGGER_CRON.equals(task.getTriggerType());
        if (cronType && (task.getCronExpr() == null || task.getCronExpr().isEmpty())) {
            log.error("[schedule] CRON task cron_expr is null/empty, cannot compute fire time, taskId={}", task.getId());
            return;
        }
        // ===== misfire 判定（§7.7）：超窗口/超一个周期不再补偿 =====
        // QUEUE 任务豁免：其轮次来自显式排队（§7.4 P2），逐轮补跑正是策略本意，跳窗即违背
        boolean queuedPolicy = ScheduleConstants.CONCURRENT_QUEUE.equals(task.getConcurrentPolicy());
        long lag = now.getTime() - next.getTime();
        if (!queuedPolicy && lag > calculator.misfireWindowMs(task)) {
            Date nextAfterSkip = once ? next : calculator.nextCycleFireTime(task, now);
            runner.writeSkipLog(task, next, ScheduleConstants.LOG_SKIPPED_MISFIRE,
                    "错过触发窗口 " + (lag / 1000) + "s，按 misfire_policy=" + task.getMisfirePolicy() + " 跳过");
            coreMapper.applyMisfire(task.getId(), nextAfterSkip,
                    nextAfterSkip == null ? null : calculator.nextCycleFireTime(task, nextAfterSkip), once);
            log.info("[schedule] misfire skipped taskId={} lag={}s", task.getId(), lag / 1000);
            metrics.misfire();
            applyPendingQuietly(task.getId());
            return;
        }
        // ===== 周期任务执行前预计算 pending（§7.2：防执行期宕机丢周期）=====
        Date pending = once ? null : calculator.nextCycleFireTime(task, next);
        // ===== CAS 抢占（带 version，双主下只有一边成功，§7.3）=====
        if (coreMapper.casTrigger(task.getId(), task.getVersion(), instanceId.getInstanceId(), pending) == 0) {
            return;
        }
        // ===== 投池；池满按 §8.2 分支回退（不占幂等键，fire_time 不变）=====
        String futureKey = task.getId() + ":" + next.getTime();
        SystemScheduleTask snapshot = task;
        Date fireTime = next;
        try {
            executorPool.submit(task.getOrganizationId(), futureKey,
                    () -> runner.runScheduled(snapshot, fireTime));
        } catch (RejectedExecutionException e) {
            metrics.poolRejected();
            coreMapper.revertPoolRejected(task.getId(), once);
            runner.writeSkipLog(task, next, ScheduleConstants.LOG_SKIPPED_POOL,
                    "执行池/组织并发已满，" + (once ? "30s 后重投本轮" : "回 READY 等待下个周期"));
            log.warn("[schedule] pool rejected, reverted taskId={} once={}", task.getId(), once);
            applyPendingQuietly(task.getId());
        }
    }

    // ================= 每 10s：实例心跳 + 主租约维持 =================

    private void heartbeatTick() {
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            boolean leader = coreMapper.renewSchedulerLock(instanceId.getInstanceId()) > 0
                    || coreMapper.tryAcquireSchedulerLock(instanceId.getInstanceId()) > 0;
            SystemScheduleInstance instance = new SystemScheduleInstance();
            instance.setInstanceId(instanceId.getInstanceId());
            instance.setRole(leader ? "LEADER" : "WORKER");
            instance.setStartTime(instanceId.getStartTime());
            instance.setStatJson(executorPool.statJson());
            logMapper.heartbeatInstance(instance);
        }
    }

    // ================= 每 30s：对账（§7.5 前置条件 + §7.7 僵尸回收）=================

    private void reconcileTick() {
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            // P3-14：对账只在主实例做（renewSchedulerLock 成功即"我仍持锁"）。此前无 leader 判定，
            // 多实例会各自回收对方刚 CAS 走的悬挂 TRIGGERED / 把对方未超时的 RUNNING 判为僵尸，互相踩
            if (coreMapper.renewSchedulerLock(instanceId.getInstanceId()) == 0) {
                return;
            }
            // ① TRIGGERED 且租约过期：悬挂回收（RTO ≤ 90s = 租约 60s + 对账 30s）
            List<SystemScheduleTask> expired = coreMapper.selectExpiredTriggers(500);
            for (SystemScheduleTask task : expired) {
                coreMapper.recoverExpiredTrigger(task.getId(), task.getOwnerInstance());
                log.warn("[schedule] recovered expired TRIGGERED taskId={} owner={}",
                        task.getId(), task.getOwnerInstance());
                applyPendingQuietly(task.getId());
            }
            // ② RUNNING 且超 2×timeout：僵尸回收（未超时的 RUNNING 行只读不改，§7.5 第二条）
            List<SystemScheduleTask> zombies = coreMapper.selectZombieRunning(500);
            for (SystemScheduleTask task : zombies) {
                boolean once = ScheduleConstants.TRIGGER_ONCE.equals(task.getTriggerType());
                Date pending = task.getPendingNextFireTime();
                Date next;
                Date nextPending;
                if (once) {
                    // 一次性任务无"下一轮"概念；ONCE 行没有 cron_expr，调周期计算器会抛异常
                    next = new Date();
                    nextPending = null;
                } else {
                    next = pending != null && pending.after(new Date()) ? pending : calculator.nextCycleFireTime(task, new Date());
                    nextPending = next == null ? null : calculator.nextCycleFireTime(task, next);
                }
                // 该轮日志行补 TIMEOUT 终态
                SystemScheduleTaskLog runningLog = logMapper.selectLatestRunningLog(task.getId());
                if (runningLog != null) {
                    logMapper.updateLogResult(runningLog.getId(), ScheduleConstants.LOG_TIMEOUT, null, null,
                            "执行超时（超过 2×timeout）由对账回收", null);
                }
                coreMapper.recoverZombieRunning(task.getId(), task.getRunningInstance(), next, nextPending, once);
                log.warn("[schedule] recovered zombie RUNNING taskId={} since={}", task.getId(), task.getRunningSince());
                metrics.execution("timeout");
                applyPendingQuietly(task.getId());
            }
        }
    }

    // ================= 每 6h：生命周期清理（§7.8）=================

    private void cleanupTick() {
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            // 一次性 DONE/CANCELLED 保留 7 天后物理删（分批）
            int deleted;
            do {
                deleted = coreMapper.purgeFinishedOnce(ScheduleConstants.ONCE_RETAIN_DAYS, 2000);
            } while (deleted >= 2000);
            // 日志保留 90 天：分区滚动 + 删前搬非 SUCCESS 进归档（§6.2/§6.3）
            int archived;
            do {
                archived = coreMapper.archiveOldLogs(ScheduleConstants.LOG_RETAIN_DAYS, 2000);
            } while (archived >= 2000);
            maintainLogPartitions();

            // P2-5：归档表清理挂到同一 tick（此前声明无调用方，归档表只增不减）
            // 归档表与在线表同一保留期：非 SUCCESS 行搬到归档是为了「可追溯失败」，不是永久存档
            coreMapper.deleteArchivedLogs(ScheduleConstants.LOG_RETAIN_DAYS, 5000);
            // P1-7：QUEUE 残留清理（任务已删/终态的排队行永不消费；超期补跑轮次已无意义）
            int purged;
            do {
                purged = coreMapper.purgeStaleQueue(ScheduleConstants.LOG_RETAIN_DAYS, 2000);
            } while (purged >= 2000);
            logMapper.purgeMetricSnapshots(ScheduleConstants.METRIC_RETAIN_DAYS);
            logMapper.purgeStaleInstances(300);
            log.info("[schedule] cleanup done: once-tasks purged, logs archived/partition-rolled");
        }
    }

    /**
     * P1-3 启动期断言：先补建分区，再检查"最远分区上界距今 ≥30 天"；不满足记 ERROR（运维可见信号），
     * 表未分区/DDL 受限时同样显式告警——静默插队失败是本条的成因，检查必须出声。
     */
    private void startupPartitionCheck() {
        if (!supportsLogPartitions()) {
            log.info("[schedule] 当前数据库无分区表（非 MySQL 家族），跳过日志分区启动检查");
            return;
        }
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            maintainLogPartitions();
            List<String> names = logMapper.selectLogPartitionNames();
            if (names == null || names.isEmpty()) {
                log.error("[schedule] 启动检查未通过：system_schedule_task_log 无可枚举分区（表未分区或 DDL 受限），"
                        + "触发日志写入可能失败，请按 sql/system_schedule.sql 的分区段补齐");
                return;
            }
            java.time.LocalDate farthest = names.stream()
                    .map(this::parsePartitionMonth)
                    .filter(java.util.Objects::nonNull)
                    .max(java.util.Comparator.naturalOrder())
                    .map(m -> m.plusMonths(1))
                    .orElse(null);
            if (farthest == null || farthest.isBefore(java.time.LocalDate.now().plusDays(30))) {
                log.error("[schedule] 启动检查未通过：日志最远分区上界 {} 距今不足 30 天，"
                        + "跨过该上界的触发日志会因无分区而写入失败（P1-3）", farthest);
            } else {
                log.info("[schedule] 启动检查通过：日志分区覆盖至 {}（instance={}）", farthest, instanceId.getInstanceId());
            }
        } catch (Exception e) {
            log.error("[schedule] 启动分区检查异常（不影响内核其余线程）", e);
        }
    }

    /**
     * 日志表按月分区滚动（§6.2）：补建分区保证未来 ≥45 天可用（防"插入无分区"失败），
     * DROP 早于保留期的分区（archiveOldLogs 已先行搬走非 SUCCESS 行）。
     * 分区名/上界由本方法按 yyyyMM 规则生成后传入 DDL（非用户输入，无注入面）。
     */
    private boolean supportsLogPartitions() {
        Boolean flag = mysqlFamily;
        if (flag == null) {
            try (java.sql.Connection c = dataSource.getConnection()) {
                flag = c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
            } catch (Exception e) {
                flag = false;
            }
            mysqlFamily = flag;
        }
        return flag;
    }

    private void maintainLogPartitions() {
        if (!supportsLogPartitions()) {
            return;
        }
        try {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate cutoff = today.minusDays(ScheduleConstants.LOG_RETAIN_DAYS);
            java.util.Set<String> existing = new java.util.HashSet<>(logMapper.selectLogPartitionNames());
            if (existing.isEmpty()) {
                return;
            }
            // ① DROP 过期整月分区（该月全部早于保留期才删）
            for (String name : new java.util.ArrayList<>(existing)) {
                java.time.LocalDate monthBegin = parsePartitionMonth(name);
                if (monthBegin == null) {
                    continue;
                }
                if (monthBegin.plusMonths(1).atStartOfDay().isBefore(cutoff.atStartOfDay())) {
                    logMapper.dropLogPartition(name);
                    existing.remove(name);
                    log.info("[schedule] dropped expired log partition {}", name);
                }
            }
            // ② ADD 分区：保证最后一个分区上界距今天 ≥45 天
            java.time.LocalDate lastUpper = existing.stream()
                    .map(this::parsePartitionMonth)
                    .filter(java.util.Objects::nonNull)
                    .max(java.util.Comparator.naturalOrder())
                    .map(m -> m.plusMonths(1))
                    .orElse(today.plusMonths(1));
            while (lastUpper.isBefore(today.plusDays(45))) {
                String name = "p" + lastUpper.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
                String upper = lastUpper.plusMonths(1).toString();
                logMapper.addLogPartition(name, upper);
                log.info("[schedule] added log partition {} (< {})", name, upper);
                lastUpper = lastUpper.plusMonths(1);
            }
        } catch (Exception e) {
            log.warn("[schedule] log partition maintenance skipped (表未分区或 DDL 受限): {}", e.getMessage());
        }
    }

    /** p202610 → 2026-10-01；非规则名返回 null */
    private java.time.LocalDate parsePartitionMonth(String name) {
        if (name == null || !name.matches("p\\d{6}")) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(name.substring(1) + "01",
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (Exception e) {
            return null;
        }
    }
}