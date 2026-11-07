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

package com.privernal.job.service;

import cn.hutool.json.JSONUtil;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.job.api.ScheduleTaskClient;
import com.privernal.job.api.TaskRef;
import com.privernal.job.action.ScheduleActionRegistry;
import com.privernal.job.context.ScheduleContextRestorer;
import com.privernal.job.domain.SystemScheduleInstance;
import com.privernal.job.domain.SystemScheduleTask;
import com.privernal.job.domain.SystemScheduleTaskLog;
import com.privernal.job.executor.ScheduleExecutorPool;
import com.privernal.job.filter.ScheduleLogFilterDTO;
import com.privernal.job.filter.ScheduleTaskFilterDTO;
import com.privernal.job.kernel.ScheduleConstants;
import com.privernal.job.kernel.ScheduleInstanceId;
import com.privernal.job.kernel.TaskExecutionRunner;
import com.privernal.job.kernel.TriggerCalculator;
import com.privernal.job.mapper.ScheduleLogMapper;
import com.privernal.job.mapper.ScheduleTaskCoreMapper;
import com.privernal.job.mapper.ScheduleTaskMapper;
import com.privernal.job.security.GroovySandboxValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 定时任务服务：管理面 CRUD/启停/手动执行/终止/预览 + 内部 API
 * （ScheduleTaskClient：注册/改期/级联取消）。
 *
 * <p>管理面写操作走 coreMapper（按 id 精确操作），列表查询走 taskMapper
 * （@PrivernalDataScope 行级范围）；内部 API 的注册/取消均带 orgId 显式定位，不依赖会话。</p>
 *
 * @author saperliu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleTaskService implements ScheduleTaskClient {

    /** 每组织活跃任务配额（§11） */
    private static final long QUOTA_ORG = 20000;
    /** 每应用活跃任务配额 */
    private static final long QUOTA_APP = 2000;

    private final ScheduleTaskCoreMapper coreMapper;
    private final ScheduleTaskMapper taskMapper;
    private final ScheduleLogMapper logMapper;
    private final TriggerCalculator calculator;
    private final GroovySandboxValidator sandboxValidator;
    private final ScheduleExecutorPool executorPool;
    private final TaskExecutionRunner runner;
    private final ScheduleContextRestorer contextRestorer;
    private final ScheduleInstanceId instanceId;
    private final com.privernal.job.kernel.PendingChangeApplier pendingApplier;

    /**
     * 字段注入而非构造注入：registry 在自身 PostConstruct 里 getBeansOfType 会沿
     * WorkflowTimeout handler → Syncer → 本服务 → registry 的链把"创建中"的 handler
     * 静默跳过（动作注册表丢 WORKFLOW_TIMEOUT/GROOVY_SCRIPT/FORM_DATA_OP 三个键），
     * 构造期循环必须在这里打断。
     */
    @org.springframework.beans.factory.annotation.Autowired
    private ScheduleActionRegistry actionRegistry;

    // ================================================================
    // 管理面
    // ================================================================

    public List<SystemScheduleTask> list(ScheduleTaskFilterDTO filter) {
        // 非超管强制当前组织；超管可显式指定（§11 数据范围）
        if (!SecurityUtils.isSuperRoleUser()) {
            filter.setOrganizationId(SecurityUtils.getOrganizationId());
        }
        return taskMapper.selectScheduleTaskList(filter);
    }

    /** P0-7：按 id 的读入口同样必须过归属，否则 A 组织可用 id 探测/读取 B 组织任务 */
    public SystemScheduleTask getById(String id) {
        return loadOwnedTask(id);
    }

    /** P2-8：按 (组织, 来源, bizKey) 定位已有行——OpenAPI/管理面重复注册的 upsert 入口 */
    private SystemScheduleTask findByIdentity(SystemScheduleTask form) {
        String orgId = StringUtils.isNotEmpty(form.getOrganizationId())
                ? form.getOrganizationId() : SecurityUtils.getOrganizationId();
        if (StringUtils.isEmpty(orgId) || StringUtils.isEmpty(form.getBizKey())) {
            return null;
        }
        String source = StringUtils.isEmpty(form.getSource())
                ? ScheduleConstants.SOURCE_MANUAL : form.getSource();
        return coreMapper.selectActiveByBizKey(orgId, source, form.getBizKey());
    }

    /**
     * 新建（MANUAL）或编辑。返回 message 区分"已生效 / 延迟生效"（§7.5：
     * TRIGGERED/RUNNING 行不改配置，改动下一轮生效）。
     */
    public Map<String, Object> save(SystemScheduleTask form) {
        if (StringUtils.isNotEmpty(form.getId())) { loadOwnedTask(form.getId()); }
        validate(form);
        String operator = StringUtils.isNotEmpty(SecurityUtils.getUserId()) ? SecurityUtils.getUserId() : "system";
        if (StringUtils.isEmpty(form.getId())) {
            // P2-8：OpenAPI 通道同 bizKey 重复注册此前直接撞 uk_biz 报错（与内部 API 的 upsert 语义不一致）。
            // 这里先按 (org, source, bizKey) 命中已有行并转为"编辑"，命中行的 org 就是本次的 org，
            // 归属约束等价于 loadOwnedTask；查不到才走新建 + 配额检查。
            // 只对外部/内部注册来源生效——界面手动新建若撞上别人的 bizKey，静默覆盖既有任务比报错更危险，
            // MANUAL 保持"撞 uk_biz 即失败"的原有守卫
            String lookupSource = StringUtils.isEmpty(form.getSource())
                    ? ScheduleConstants.SOURCE_MANUAL : form.getSource();
            SystemScheduleTask byKey = ScheduleConstants.SOURCE_MANUAL.equals(lookupSource)
                    ? null : findByIdentity(form);
            if (byKey != null) {
                form.setId(byKey.getId());
            } else {
                checkQuota(form);
                SystemScheduleTask task = buildForInsert(form, operator);
                coreMapper.insertTask(task);
                return result(task.getId(), "已创建");
            }
        }
        SystemScheduleTask existing = coreMapper.selectById(form.getId());
        if (existing == null || "Y".equals(existing.getDeletedFlag())) {
            throw new IllegalArgumentException("任务不存在或已删除");
        }
        if (ScheduleConstants.STATUS_TRIGGERED.equals(existing.getStatus())
                || ScheduleConstants.STATUS_RUNNING.equals(existing.getStatus())) {
            // P1-4：在途编辑不再丢弃。validate 已过，把这次提交原样登记到 pending_change，
            // 本轮执行回写后由 PendingChangeApplier 应用（upsertOverwrite 内含清空暂存）
            if (coreMapper.updatePendingChange(existing.getId(), JSONUtil.toJsonStr(form)) == 0) {
                return result(existing.getId(), "任务状态已变化，修改未生效，请刷新后重试");
            }
            return result(existing.getId(), "任务正在触发/执行中，本次修改已登记，本轮结束后自动生效");
        }
        SystemScheduleTask normalized = buildForUpdate(form, existing, operator);
        int rows = coreMapper.upsertOverwrite(normalized);
        if (rows == 0) {
            return result(existing.getId(), "任务状态已变化，修改未生效，请刷新后重试");
        }
        return result(existing.getId(), "已生效（version+1，READY 行下轮扫描即按新配置执行）");
    }

    /** 启用/暂停（§12.1：回显必须区分"已生效"与"下一轮生效"） */
    public Map<String, Object> changeStatus(String id, String status) {
        loadOwnedTask(id);
        SystemScheduleTask task = coreMapper.selectById(id);
        if (task == null || "Y".equals(task.getDeletedFlag())) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (ScheduleConstants.STATUS_TRIGGERED.equals(task.getStatus())
                || ScheduleConstants.STATUS_RUNNING.equals(task.getStatus())) {
            // P1-4：在途启停同样登记（叠加在已有暂存之上，不冲掉先前的配置变更）
            String staged = com.privernal.job.kernel.PendingChangeApplier
                    .withStatus(task.getPendingChange(), status);
            if (coreMapper.updatePendingChange(id, staged) == 0) {
                return result(id, "任务状态已变化，操作未生效，请刷新后重试");
            }
            return result(id, "本轮正在执行，" + (ScheduleConstants.STATUS_PAUSED.equals(status)
                    ? "暂停" : "启用") + "已登记，本轮结束后自动生效");
        }
        Date next = null;
        if (ScheduleConstants.STATUS_READY.equals(status)) {
            next = calculator.buildInitialNext(task);
        }
        coreMapper.changeStatus(id, status, next);
        return result(id, ScheduleConstants.STATUS_PAUSED.equals(status) ? "已暂停（在途执行不受影响）" : "已启用");
    }

    public int batchChangeStatus(String appId, String source, String status) {
        String orgId = SecurityUtils.getOrganizationId();
        return coreMapper.batchChangeStatus(orgId, appId, source, status);
    }

    /** P0-7：by-id 入口统一归属校验——任务不属于当前会话组织即按"不存在"处理（防跨组织越权，不暴露 id 存在性） */
    private SystemScheduleTask loadOwnedTask(String id) {
        SystemScheduleTask task = coreMapper.selectById(id);
        if (task == null || "Y".equals(task.getDeletedFlag())) {
            throw new IllegalArgumentException("任务不存在");
        }
        String orgId = SecurityUtils.getOrganizationId();
        if (StringUtils.isNotEmpty(orgId) && !orgId.equals(task.getOrganizationId())) {
            throw new IllegalArgumentException("任务不存在");
        }
        return task;
    }

    public int delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要删除的任务");
        }
        String operator = StringUtils.isNotEmpty(SecurityUtils.getUserId()) ? SecurityUtils.getUserId() : "system";
        String orgId = SecurityUtils.getOrganizationId();
        // P0-7：orgId 取不到时不能照发 SQL——softDeleteCancel 带 organization_id 条件，
        // 空值会让删除静默影响 0 行却回显成功（比越权更难排查）
        if (StringUtils.isEmpty(orgId)) {
            throw new IllegalArgumentException("缺少组织上下文，拒绝删除");
        }
        int affected = coreMapper.softDeleteCancel(ids, operator, orgId);
        // P1-5：执行中的任务被 SQL 状态条件挡掉，0 行不能回显成功
        if (affected == 0) {
            throw new IllegalArgumentException("任务不存在或正在执行中，执行中的任务请先终止后再删除");
        }
        return affected;
    }

    /** 手动执行（§11：不能只靠幂等键，running_instance 占位拒绝连点） */
    public Map<String, Object> run(String id) {
        SystemScheduleTask task = loadOwnedTask(id);
        if (ScheduleConstants.STATUS_DONE.equals(task.getStatus())
                || ScheduleConstants.STATUS_CANCELLED.equals(task.getStatus())) {
            throw new IllegalArgumentException("任务已终态（DONE/CANCELLED），请先编辑保存复活后再执行");
        }
        if (coreMapper.casManualRun(id, instanceId.getInstanceId()) == 0) {
            return result(id, "任务正在执行，请稍后再试");
        }
        String futureKey = id + ":manual:" + System.currentTimeMillis();
        try {
            executorPool.submit(task.getOrganizationId(), futureKey, () -> runner.runManual(task));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            coreMapper.releaseManualRun(id, instanceId.getInstanceId());
            return result(id, "执行池已满，请稍后再试");
        }
        return result(id, "已提交手动执行");
    }

    /** 终止运行中（§11：日志行 CANCELLED + Future.cancel 尽力中断） */
    public Map<String, Object> terminate(String id) {
        SystemScheduleTask task = loadOwnedTask(id);
        SystemScheduleTaskLog runningLog = logMapper.selectLatestRunningLog(id);
        if (runningLog != null) {
            logMapper.updateLogResult(runningLog.getId(), ScheduleConstants.LOG_CANCELLED, null, null,
                    "管理员手动终止", null);
        }
        boolean once = ScheduleConstants.TRIGGER_ONCE.equals(task.getTriggerType());
        Date pending = task.getPendingNextFireTime();
        Date next;
        Date nextPending;
        if (once) {
            next = new Date();
            nextPending = null;
        } else {
            next = pending != null && pending.after(new Date()) ? pending : calculator.nextCycleFireTime(task, new Date());
            nextPending = next == null ? null : calculator.nextCycleFireTime(task, next);
        }
        coreMapper.terminateRunning(id, instanceId.getInstanceId(), next, nextPending, once);
        return result(id, "已发出终止指令（执行线程将被尽力中断）");
    }

    /** 未来 n 次触发预览（§12.1；jitter 确定性保证预览与实际一致） */
    public Map<String, Object> preview(String id, int n) {
        // P0-7：预览会泄露任务的 cron/触发计划，同样过归属（loadOwnedTask 内已抛"任务不存在"）
        SystemScheduleTask task = loadOwnedTask(id);
        Map<String, Object> data = new LinkedHashMap<>();
        if (ScheduleConstants.TRIGGER_CRON.equals(task.getTriggerType())) {
            int offset = calculator.jitterOffset(task.getId(),
                    task.getJitterSeconds() == null ? 0 : task.getJitterSeconds());
            List<Date> theory = calculator.previewNext(task.getCronExpr(), n);
            data.put("fireTimes", theory.stream().map(d -> new Date(d.getTime() + offset * 1000L)).toList());
            data.put("jitterSeconds", offset);
            data.put("note", offset > 0 ? "含确定性 jitter 偏移 " + offset + "s（有意延迟，已叠加到实际触发时刻）" : "");
        } else if (ScheduleConstants.TRIGGER_FIXED_DELAY.equals(task.getTriggerType())) {
            long interval = (task.getIntervalSeconds() == null ? 60 : task.getIntervalSeconds()) * 1000L;
            Date cursor = task.getNextFireTime() != null ? task.getNextFireTime() : new Date();
            java.util.ArrayList<Date> times = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                times.add(cursor);
                cursor = new Date(cursor.getTime() + interval);
            }
            data.put("fireTimes", times);
            data.put("note", "按固定间隔推算");
        } else {
            data.put("fireTimes", task.getNextFireTime() == null ? List.of() : List.of(task.getNextFireTime()));
            data.put("note", "一次性任务");
        }
        // §7.1/§12.1 排队延迟预览：每个预览时刻统计同时刻（2 秒窗口）聚集的 READY 行数，
        // 估算延迟=聚集数/2000 上限；聚集超过单轮 LIMIT 时提示配置 jitter
        @SuppressWarnings("unchecked")
        List<Date> fireTimes = (List<Date>) data.get("fireTimes");
        if (fireTimes != null && !fireTimes.isEmpty()) {
            List<Map<String, Object>> queue = new java.util.ArrayList<>();
            for (Date fireTime : fireTimes) {
                long cluster = coreMapper.countReadyInWindow(fireTime, new Date(fireTime.getTime() + 2000L));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fireTime", fireTime);
                item.put("cluster", cluster);
                item.put("estDelaySeconds", cluster * 1000L / ScheduleConstants.SCAN_LIMIT / 1000L);
                queue.add(item);
            }
            data.put("queue", queue);
            long maxCluster = queue.stream().mapToLong(m -> (long) m.get("cluster")).max().orElse(0);
            if (maxCluster > ScheduleConstants.SCAN_LIMIT) {
                data.put("queueWarning", "同时刻聚集 " + maxCluster + " 个任务已超单轮扫描上限 "
                        + ScheduleConstants.SCAN_LIMIT + "，最坏排队延迟约 " + (maxCluster / ScheduleConstants.SCAN_LIMIT)
                        + "s——建议为整点任务配置 jitter_seconds=60 削峰");
            }
        }
        return data;
    }

    public List<SystemScheduleTaskLog> listLogs(ScheduleLogFilterDTO filter) {
        if (!SecurityUtils.isSuperRoleUser()) {
            filter.setOrganizationId(SecurityUtils.getOrganizationId());
        }
        return logMapper.selectScheduleLogList(filter);
    }

    public SystemScheduleTaskLog getLog(Long id) {
        return logMapper.selectScheduleLogById(id);
    }

    /** 失败手动重跑（§12.1）：按日志快照重执行一次（attempt+1，fire_time=now 新行） */
    public Map<String, Object> retryLog(Long logId) {
        // 重试按日志找任务，同样校验归属
        SystemScheduleTaskLog logRow = logMapper.selectScheduleLogById(logId);
        if (logRow == null) {
            throw new IllegalArgumentException("日志不存在");
        }
        loadOwnedTask(logRow.getTaskId());
        SystemScheduleTask task = coreMapper.selectById(logRow.getTaskId());
        if (task == null) {
            throw new IllegalArgumentException("原任务已删除，无法重跑");
        }
        return run(task.getId());
    }

    public Map<String, Object> instanceState() {
        Map<String, Object> data = new HashMap<>();
        List<SystemScheduleInstance> instances = logMapper.selectInstanceList();
        // 租约已过期的行是停机/被 kill 的实例（行要等 6h 清理周期才物理删），
        // role 字段停留在最后一次心跳值，直接展示会出现两个 LEADER；这里只改展示为 OFFLINE，不落库
        Date now = new Date();
        for (SystemScheduleInstance instance : instances) {
            if (instance.getLeaseUntil() == null || instance.getLeaseUntil().before(now)) {
                instance.setRole("OFFLINE");
            }
        }
        data.put("instances", instances);
        data.put("locks", logMapper.selectLockState());
        data.put("local", instanceId.getInstanceId());
        data.put("pool", executorPool.statJson());
        return data;
    }

    /** 配额水位（§12.1 配额视图） */
    public Map<String, Object> quota() {
        String orgId = SecurityUtils.getOrganizationId();
        Map<String, Object> data = new LinkedHashMap<>();
        if (StringUtils.isEmpty(orgId)) {
            return data;
        }
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            long orgActive = coreMapper.countActive(orgId, null);
            data.put("orgId", orgId);
            data.put("orgActive", orgActive);
            data.put("orgQuota", QUOTA_ORG);
            data.put("orgUsage", percent(orgActive, QUOTA_ORG));
            // T14：分应用明细（应用名为空串的系统级任务归入"（无应用）"）
            data.put("apps", coreMapper.countActiveByApp(orgId));
        }
        return data;
    }

    /**
 * 任务报警列表（D5-C 一期落点）：全部告警项查库聚合 + 阈值判定。
     * hit=true 即触发告警；前端轮询展示，站内信推送由各告警项自行触发（自动暂停已实现）。
     */
    public Map<String, Object> alarmList() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> alarms = new java.util.ArrayList<>();
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            Map<String, Long> statusCount = new LinkedHashMap<>();
            for (Map<String, Object> row : logMapper.selectLogStatusCountsLastHour()) {
                statusCount.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
            }
            long executed = statusCount.getOrDefault("SUCCESS", 0L) + statusCount.getOrDefault("FAILED", 0L)
                    + statusCount.getOrDefault("TIMEOUT", 0L);
            long misfires = statusCount.getOrDefault("SKIPPED_MISFIRE", 0L);
            double misfireRate = executed + misfires == 0 ? 0 : misfires * 100.0 / (executed + misfires);
            alarms.add(alarm("MISFIRE_RATE", "misfire 率", String.format("%.2f%%", misfireRate),
                    "> 0.1%/小时", misfireRate > 0.1, "近 1 小时错过触发窗口 " + misfires + " 次 / 执行 " + executed + " 次"));

            long skippedPool = statusCount.getOrDefault("SKIPPED_POOL", 0L);
            alarms.add(alarm("SKIPPED_POOL", "执行池拒绝次数", String.valueOf(skippedPool),
                    "> 0", skippedPool > 0, "近 1 小时执行池/组织并发满被拒次数，容量测算需重看"));

            long timeoutCount = statusCount.getOrDefault("TIMEOUT", 0L);
            alarms.add(alarm("TIMEOUT", "执行超时次数", String.valueOf(timeoutCount),
                    "> 0", timeoutCount > 0, "近 1 小时超 2×timeout 被对账回收的执行"));

            long hanging = coreMapper.countHangingTriggers();
            alarms.add(alarm("HANGING_TRIGGERED", "悬挂 TRIGGERED 行", String.valueOf(hanging),
                    "> 0", hanging > 0, "租约过期未被对账回收的在途行（正常应 ≤ 90s 自愈）"));

            long staleReady = coreMapper.countReadyStale();
            alarms.add(alarm("READY_STALE", "到期未触发 READY 行", String.valueOf(staleReady),
                    "> 0 持续", staleReady > 0, "到期超 60s 仍未触发；持续 >0 即调度停滞（检查数据权限豁免/锁行）"));

            // 单应用聚合失败率（§7.7/§15：一次性任务单任务阈值不可达，走来源/应用维度）
            List<Map<String, Object>> appStats = logMapper.selectAppFailureStatsLastHour();
            List<Map<String, Object>> hotApps = new java.util.ArrayList<>();
            for (Map<String, Object> row : appStats) {
                long total = ((Number) row.get("total")).longValue();
                long failed = ((Number) row.get("failed")).longValue();
                double rate = total == 0 ? 0 : failed * 100.0 / total;
                if (failed > 20 && rate > 10) {
                    Map<String, Object> hot = new LinkedHashMap<>();
                    hot.put("appId", row.get("appId"));
                    hot.put("total", total);
                    hot.put("failed", failed);
                    hot.put("rate", String.format("%.1f%%", rate));
                    hotApps.add(hot);
                }
            }
            alarms.add(alarm("APP_FAILURE_RATE", "单应用聚合失败率", hotApps.size() + " 个应用命中",
                    "失败率>10% 且失败>20", !hotApps.isEmpty(), "近 1 小时按应用聚合"));
            data.put("appFailures", hotApps);

            // 池水位与配额（当前快照）
            String pool = executorPool.statJson();
            alarms.add(alarm("POOL_WATER", "执行池水位", pool, "queue>8000", pool.contains("\"queue\":8") || pool.contains("\"queue\":9"),
                    "active/queue/running 实时值见实例状态页"));

            // ===== 指标快照趋势（D5-C 二期方案 B：30 分钟窗口差分）=====
            data.put("metricTrend", buildMetricTrend());
            String orgId = SecurityUtils.getOrganizationId();
            if (StringUtils.isNotEmpty(orgId)) {
                long orgActive = coreMapper.countActive(orgId, null);
                double usage = orgActive * 100.0 / QUOTA_ORG;
                alarms.add(alarm("QUOTA", "组织配额使用率", usage + "%", "> 90%", usage > 90,
                        "活跃任务 " + orgActive + " / " + QUOTA_ORG));
            }
        }
        data.put("alarms", alarms);
        data.put("hitCount", alarms.stream().filter(a -> Boolean.TRUE.equals(a.get("hit"))).count());
        return data;
    }

    /**
     * 指标趋势（D5-C 二期方案 B）：读 30 分钟快照窗口，计数器差分算窗口增量，
     * cas.round 差分算窗口均值与 max。快照未就绪（任务刚注册）时返回空列表。
     */
    private List<Map<String, Object>> buildMetricTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        List<Map<String, Object>> snaps = logMapper.selectMetricSnapshots(30);
        if (snaps == null || snaps.size() < 2) {
            return trend;
        }
        java.util.function.BiFunction<String, String, Double> delta =
                (name, statistic) -> delta(snaps, name, statistic, null, null);
        java.util.function.BiFunction<String, String, Double> latest = (name, statistic) -> {
            Double v = null;
            for (Map<String, Object> snap : snaps) {
                if (name.equals(snap.get("metricName")) && statistic.equals(snap.get("statistic"))) {
                    v = ((Number) snap.get("value")).doubleValue();
                }
            }
            return v;
        };
        addTrendRow(trend, "执行成功（30 分钟）", deltaByOutcome(snaps, "success"));
        addTrendRow(trend, "执行失败（30 分钟）", deltaByOutcome(snaps, "failed"));
        addTrendRow(trend, "错过触发（30 分钟）", delta.apply("schedule.misfires", "COUNT"));
        addTrendRow(trend, "池拒绝（30 分钟）", delta.apply("schedule.pool.rejected", "COUNT"));
        addTrendRow(trend, "排队轮次（30 分钟）", delta.apply("schedule.queue.enqueued", "COUNT"));
        Double casCountDelta = delta.apply("schedule.cas.round", "COUNT");
        Double casTotalDelta = delta.apply("schedule.cas.round", "TOTAL_TIME");
        Double casMax = latest.apply("schedule.cas.round", "MAX");
        if (casCountDelta != null && casCountDelta > 0 && casTotalDelta != null) {
            addTrendRow(trend, "扫描轮均值（30 分钟）", String.format("%.3fs", casTotalDelta / casCountDelta));
        }
        addTrendRow(trend, "扫描轮最大（30 分钟）", casMax == null ? null : String.format("%.3fs", casMax));
        addTrendRow(trend, "执行池活跃（当前）", latest.apply("schedule.pool.active", "VALUE") == null
                ? null : String.valueOf(latest.apply("schedule.pool.active", "VALUE").longValue()));
        addTrendRow(trend, "执行池队列（当前）", latest.apply("schedule.pool.queue", "VALUE") == null
                ? null : String.valueOf(latest.apply("schedule.pool.queue", "VALUE").longValue()));
        return trend;
    }

    private Double deltaByOutcome(List<Map<String, Object>> snaps, String outcome) {
        Double delta = delta(snaps, "schedule.executions", "COUNT", "tagOutcome", outcome);
        return delta == null ? null : Math.max(delta, 0d);
    }

    /**
     * 计数器窗口差分。计数器是 JVM 内存态：应用重启会归零，跨重启差分出现负值
     * 属预期噪音——统一钳 0（负增量 = 期间发生过重启，重启本身由实例心跳缺席观测）。
     */
    private Double delta(List<Map<String, Object>> snaps, String name, String statistic,
                         String tagKey, String tagValue) {
        Double first = null;
        Double last = null;
        for (Map<String, Object> snap : snaps) {
            if (name.equals(snap.get("metricName")) && statistic.equals(snap.get("statistic"))
                    && (tagKey == null || tagValue == null || tagValue.equals(snap.get(tagKey)))) {
                double v = ((Number) snap.get("value")).doubleValue();
                if (first == null) {
                    first = v;
                }
                last = v;
            }
        }
        return first == null || last == null ? null : Math.max(last - first, 0d);
    }

    private void addTrendRow(List<Map<String, Object>> trend, String title, Object value) {
        if (value == null) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("metric", title);
        row.put("value", value);
        trend.add(row);
    }

    private Map<String, Object> alarm(String code, String title, String value, String threshold, boolean hit, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("title", title);
        item.put("value", value);
        item.put("threshold", threshold);
        item.put("hit", hit);
        item.put("detail", detail);
        return item;
    }

    // ================================================================
    // 内部 API（ScheduleTaskClient，§12.2；签名一律带 orgId）
    // ================================================================

    @Override
    public TaskRef registerOnce(String orgId, String source, String bizKey, Date fireTime,
                                 String actionType, Object actionConfig, Object bizContext, String databaseName) {
        return doRegister(orgId, null, source, bizKey, null, ScheduleConstants.TRIGGER_ONCE, null, null,
                actionType, actionConfig, bizContext, databaseName, fireTime, "一次性任务");
    }

    @Override
    public TaskRef registerCron(String orgId, String source, String bizKey, String taskCode, String cron,
                                 String actionType, Object actionConfig, String databaseName) {
        return doRegister(orgId, null, source, bizKey, taskCode, ScheduleConstants.TRIGGER_CRON, cron, null,
                actionType, actionConfig, null, databaseName, null, "周期任务");
    }

    @Override
    public TaskRef registerCronWithApp(String orgId, String appId, String source, String bizKey, String taskCode,
                                        String cron, String actionType, Object actionConfig, String databaseName) {
        return doRegister(orgId, appId, source, bizKey, taskCode, ScheduleConstants.TRIGGER_CRON, cron, null,
                actionType, actionConfig, null, databaseName, null, "周期任务");
    }

    @Override
    public TaskRef registerOnceWithApp(String orgId, String appId, String source, String bizKey, Date fireTime,
                                        String actionType, Object actionConfig, Object bizContext, String databaseName) {
        return doRegister(orgId, appId, source, bizKey, null, ScheduleConstants.TRIGGER_ONCE, null, null,
                actionType, actionConfig, bizContext, databaseName, fireTime, "一次性任务");
    }

    @Override
    public TaskRef registerFixedDelay(String orgId, String source, String bizKey, String taskCode,
                                       int intervalSeconds, String actionType, Object actionConfig, String databaseName) {
        return doRegister(orgId, null, source, bizKey, taskCode, ScheduleConstants.TRIGGER_FIXED_DELAY, null, intervalSeconds,
                actionType, actionConfig, null, databaseName, null, "固定间隔任务");
    }

    private TaskRef doRegister(String orgId, String appId, String source, String bizKey, String taskCode,
                               String triggerType, String cron, Integer intervalSeconds,
                               String actionType, Object actionConfig, Object bizContext,
                               String databaseName, Date onceFireTime, String title) {
        // P1-1：内部注册通道与管理面同关——DESIGNER/DESIGNER_DATA 的 Groovy 脚本此前绕过静态校验
        if ("GROOVY_SCRIPT".equals(actionType) && actionConfig instanceof Map<?, ?> actionMap) {
            Object script = actionMap.get("script");
            if (script != null) {
                sandboxValidator.validate(String.valueOf(script));
            }
        }
        if (StringUtils.isEmpty(orgId) || StringUtils.isEmpty(source) || StringUtils.isEmpty(actionType)) {
            throw new IllegalArgumentException("orgId/source/actionType 必填");
        }
        String normalizedBizKey = StringUtils.isEmpty(bizKey) ? UUID.randomUUID().toString() : bizKey;
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            SystemScheduleTask existing = coreMapper.selectActiveByBizKey(orgId, source, normalizedBizKey);
            SystemScheduleTask task = existing == null ? new SystemScheduleTask() : existing;
            if (existing == null) {
                task.setId(UUID.randomUUID().toString().replace("-", ""));
                task.setOrganizationId(orgId);
                task.setSource(source);
                task.setBizKey(normalizedBizKey);
                task.setTenantId("system-default");
                task.setDepartmentId("");
                task.setAppId(StringUtils.isEmpty(appId) ? "" : appId);
                task.setCreatedBy("system");
                task.setDeletedFlag("N");
                task.setStatus(ScheduleConstants.STATUS_READY);
                task.setVersion(0L);
                task.setTriggerCount(0L);
                task.setFailCount(0L);
                task.setCurrentAttempt(1);
            }
            task.setTaskCode(taskCode);
            task.setTitle(title + (taskCode == null ? ":" + abbreviateKey(normalizedBizKey) : ":" + taskCode));
            task.setTriggerType(triggerType);
            task.setCronExpr(cron);
            task.setIntervalSeconds(intervalSeconds);
            task.setPeriodSeconds(ScheduleConstants.TRIGGER_FIXED_DELAY.equals(triggerType)
                    ? (intervalSeconds == null ? null : intervalSeconds.longValue())
                    : (cron == null ? null : calculator.computePeriodSeconds(cron)));
            task.setJitterSeconds(0);
            task.setMisfirePolicy(ScheduleConstants.MISFIRE_COMPENSATE);
            task.setMisfireWindowSeconds(300);
            task.setConcurrentPolicy(ScheduleConstants.CONCURRENT_FORBID);
            task.setTimeoutSeconds(300);
            task.setMaxRetry(3);
            task.setActionType(actionType);
            task.setActionConfig(toJson(actionConfig));
            task.setBizContext(toJson(bizContext));
            task.setDatabaseName(StringUtils.isEmpty(databaseName) ? "master" : databaseName);
            task.setRemark("registered by " + source);
            task.setNextFireTime(ScheduleConstants.TRIGGER_ONCE.equals(triggerType)
                    ? onceFireTime : calculator.buildInitialNext(task));
            task.setPendingNextFireTime(null);

            if (existing == null) {
                coreMapper.insertTask(task);
                log.info("[schedule] registered task {} bizKey={} fire={}", task.getId(), normalizedBizKey, task.getNextFireTime());
                return TaskRef.of(task.getId(), normalizedBizKey, false, false);
            }
            // ===== upsert 三分支（§12.2）=====
            String status = existing.getStatus();
            if (ScheduleConstants.STATUS_TRIGGERED.equals(status) || ScheduleConstants.STATUS_RUNNING.equals(status)) {
                return TaskRef.of(existing.getId(), normalizedBizKey, true, true);
            }
            if (ScheduleConstants.STATUS_DONE.equals(status) || ScheduleConstants.STATUS_CANCELLED.equals(status)) {
                coreMapper.upsertRevive(task);
                log.info("[schedule] revived task {} bizKey={}", existing.getId(), normalizedBizKey);
                return TaskRef.of(existing.getId(), normalizedBizKey, true, false);
            }
            coreMapper.upsertOverwrite(task);
            return TaskRef.of(existing.getId(), normalizedBizKey, true, false);
        }
    }

    /**
     * 应用包导入专用注册（task-upgrade P1-2）。与内部 API 的 doRegister 分开写，因为两者的判重键不同：
     * 跨环境只有 task_code 稳定（biz_key 含组织前缀，是环境内产物），且导入必须尊重 overwrite、
     * 透传包里的调度属性，而不是回落到 doRegister 的默认值。
     *
     * @param template  由包内容拼装的候选行（缺省项由本方法补齐；nextFireTime 对 ONCE 取包内原时刻）
     * @param overwrite 目标环境已存在同码任务时是否覆盖
     * @return 导入结论：CREATED / UPDATED / REVIVED / SKIPPED:xxx / REJECTED:xxx
     */
    public String importFromPackage(SystemScheduleTask template, boolean overwrite) {
        if (template == null || StringUtils.isEmpty(template.getOrganizationId())) {
            return "REJECTED:缺少组织上下文，无法导入任务";
        }
        boolean once = ScheduleConstants.TRIGGER_ONCE.equals(template.getTriggerType());
        if (!once && StringUtils.isEmpty(template.getTaskCode())) {
            // uk_code(app_id, task_code) 是跨环境唯一稳定的判重键，缺它只能拒——
            // 以前静默按 bizKey 判重，同码任务会在目标环境重复建出来
            return "REJECTED:CRON/FIXED_DELAY 任务缺少 task_code，无法按应用判重导入";
        }
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            SystemScheduleTask existing = StringUtils.isEmpty(template.getTaskCode())
                    ? null : coreMapper.selectActiveByTaskCode(template.getAppId(), template.getTaskCode());
            if (existing != null && !overwrite) {
                return "SKIPPED:同码任务已存在（未开启覆盖）";
            }
            if (existing != null && (ScheduleConstants.STATUS_TRIGGERED.equals(existing.getStatus())
                    || ScheduleConstants.STATUS_RUNNING.equals(existing.getStatus()))) {
                return "SKIPPED:同码任务正在触发/执行中，请稍后重导";
            }
            if (once && (template.getNextFireTime() == null || !template.getNextFireTime().after(new Date()))) {
                // 绝不"导入即执行"（P1-2 原缺陷：把 next_fire_time 置 new Date()）
                return "SKIPPED:一次性任务的原定触发时刻缺失或已过期，请到目标环境重新配置";
            }
            fillImportDefaults(template);
            if (once) {
                template.setStatus(ScheduleConstants.STATUS_READY);
            } else {
                template.setNextFireTime(calculator.buildInitialNext(template));
            }
            if (template.getNextFireTime() == null) {
                return "REJECTED:无法计算触发时间（cron/interval 非法）";
            }
            validate(template);
            if (existing == null) {
                template.setId(UUID.randomUUID().toString().replace("-", ""));
                template.setSource(StringUtils.isEmpty(template.getSource())
                        ? ScheduleConstants.SOURCE_DESIGNER : template.getSource());
                template.setBizKey(StringUtils.isEmpty(template.getBizKey())
                        ? "import:" + template.getAppId() + ":" + template.getTaskCode() : template.getBizKey());
                template.setTenantId("system-default");
                template.setDepartmentId("");
                template.setAppId(StringUtils.isEmpty(template.getAppId()) ? "" : template.getAppId());
                template.setCreatedBy(StringUtils.isEmpty(template.getCreatedBy()) ? "system" : template.getCreatedBy());
                template.setDeletedFlag("N");
                template.setStatus(ScheduleConstants.STATUS_READY);
                template.setVersion(0L);
                template.setTriggerCount(0L);
                template.setFailCount(0L);
                template.setCurrentAttempt(1);
                template.setPendingNextFireTime(null);
                coreMapper.insertTask(template);
                log.info("[schedule] imported task {} taskCode={} fire={}",
                        template.getId(), template.getTaskCode(), template.getNextFireTime());
                return "CREATED";
            }
            // 覆盖：保留身份列（id/org/source/bizKey/createdBy/appId），只换配置面
            SystemScheduleTask merged = existing;
            merged.setTitle(template.getTitle());
            merged.setTriggerType(template.getTriggerType());
            merged.setCronExpr(template.getCronExpr());
            merged.setIntervalSeconds(template.getIntervalSeconds());
            merged.setPeriodSeconds(template.getPeriodSeconds());
            merged.setJitterSeconds(template.getJitterSeconds());
            merged.setMisfirePolicy(template.getMisfirePolicy());
            merged.setMisfireWindowSeconds(template.getMisfireWindowSeconds());
            merged.setConcurrentPolicy(template.getConcurrentPolicy());
            merged.setTimeoutSeconds(template.getTimeoutSeconds());
            merged.setMaxRetry(template.getMaxRetry());
            merged.setActionType(template.getActionType());
            merged.setActionConfig(template.getActionConfig());
            merged.setBizContext(template.getBizContext());
            merged.setDatabaseName(template.getDatabaseName());
            merged.setRemark(template.getRemark());
            merged.setNextFireTime(template.getNextFireTime());
            merged.setPendingNextFireTime(null);
            boolean terminal = ScheduleConstants.STATUS_DONE.equals(existing.getStatus())
                    || ScheduleConstants.STATUS_CANCELLED.equals(existing.getStatus());
            int rows = terminal ? coreMapper.upsertRevive(merged) : coreMapper.upsertOverwrite(merged);
            if (rows == 0) {
                return "SKIPPED:任务状态已变化，覆盖未生效，请重导";
            }
            log.info("[schedule] import overwrite task {} taskCode={}", existing.getId(), existing.getTaskCode());
            return terminal ? "REVIVED" : "UPDATED";
        }
    }

    /** 导入时的属性缺省补齐（包里有什么就用什么，只在字段为空时兜底） */
    private void fillImportDefaults(SystemScheduleTask template) {
        if (template.getJitterSeconds() == null) {
            template.setJitterSeconds(0);
        }
        if (StringUtils.isEmpty(template.getMisfirePolicy())) {
            template.setMisfirePolicy(ScheduleConstants.MISFIRE_COMPENSATE);
        }
        if (template.getMisfireWindowSeconds() == null) {
            template.setMisfireWindowSeconds(300);
        }
        if (StringUtils.isEmpty(template.getConcurrentPolicy())) {
            template.setConcurrentPolicy(ScheduleConstants.CONCURRENT_FORBID);
        }
        if (template.getTimeoutSeconds() == null) {
            template.setTimeoutSeconds(300);
        }
        if (template.getMaxRetry() == null) {
            template.setMaxRetry(3);
        }
        if (StringUtils.isEmpty(template.getDatabaseName())) {
            template.setDatabaseName("master");
        }
        if (StringUtils.isEmpty(template.getTitle())) {
            template.setTitle("导入任务:" + (template.getTaskCode() == null ? template.getBizKey() : template.getTaskCode()));
        }
        if (ScheduleConstants.TRIGGER_FIXED_DELAY.equals(template.getTriggerType())) {
            template.setPeriodSeconds(template.getIntervalSeconds() == null ? null
                    : template.getIntervalSeconds().longValue());
        } else if (ScheduleConstants.TRIGGER_CRON.equals(template.getTriggerType())) {
            template.setPeriodSeconds(calculator.computePeriodSeconds(template.getCronExpr()));
        }
    }

    @Override
    public boolean reschedule(String orgId, String source, String bizKey, Date newFireTime) {
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            SystemScheduleTask existing = coreMapper.selectActiveByBizKey(orgId, source, bizKey);
            if (existing == null) {
                return false;
            }
            int rows = coreMapper.reschedule(existing.getId(), newFireTime);
            if (rows > 0) {
                return true;
            }
            // P1-4：行在途时 reschedule 打不动——改期同样登记到 pending_change，
            // 本轮结束后由应用者落地；status/attempt 等运行态列不能进暂存（会被误当成启停指令）
            boolean inFlight = ScheduleConstants.STATUS_TRIGGERED.equals(existing.getStatus())
                    || ScheduleConstants.STATUS_RUNNING.equals(existing.getStatus());
            if (!inFlight) {
                log.info("[schedule] reschedule 未命中（状态 {}）taskId={} bizKey={}",
                        existing.getStatus(), existing.getId(), bizKey);
                return false;
            }
            SystemScheduleTask staged = JSONUtil.toBean(JSONUtil.toJsonStr(existing), SystemScheduleTask.class);
            staged.setStatus(null);
            staged.setPendingChange(null);
            staged.setNextFireTime(newFireTime);
            if (coreMapper.updatePendingChange(existing.getId(), JSONUtil.toJsonStr(staged)) > 0) {
                log.info("[schedule] 在途改期已登记，本轮结束后生效 taskId={} newFireTime={}",
                        existing.getId(), newFireTime);
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean cancel(String orgId, String source, String bizKey) {
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            if (coreMapper.cancelByBizKey(orgId, source, bizKey) > 0) {
                return true;
            }
            // P1-5：TRIGGERED/RUNNING 行不再被 cancel 硬改，取消意图登记为在途变更（本轮后生效）
            return coreMapper.stageCancelInFlightByBizKey(orgId, source, bizKey) > 0;
        }
    }

    @Override
    public int cancelByPrefix(String orgId, String source, String bizPrefix) {
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            int cancelled = coreMapper.cancelByPrefix(orgId, source, bizPrefix);
            // P1-5：同前缀里在途的行登记取消意图，避免"删了规则、正在跑的这一轮之后又发一次"
            int staged = coreMapper.stageCancelInFlightByPrefix(orgId, source, bizPrefix);
            if (staged > 0) {
                log.info("[schedule] {} 条在途任务已登记取消意图，本轮结束后生效（prefix={}）", staged, bizPrefix);
            }
            return cancelled;
        }
    }

    @Override
    public List<String> listActiveBizKeys(String orgId, String source, String bizPrefix) {
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            return coreMapper.selectActiveByBizPrefix(orgId, source, bizPrefix)
                    .stream().map(SystemScheduleTask::getBizKey).toList();
        }
    }

    @Override
    public Map<String, Object> selectByBizKey(String orgId, String source, String bizKey) {
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            SystemScheduleTask task = coreMapper.selectActiveByBizKey(orgId, source, bizKey);
            if (task == null) {
                return null;
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", task.getId());
            view.put("taskCode", task.getTaskCode());
            view.put("title", task.getTitle());
            view.put("bizKey", task.getBizKey());
            view.put("triggerType", task.getTriggerType());
            view.put("cronExpr", task.getCronExpr());
            view.put("nextFireTime", task.getNextFireTime());
            view.put("status", task.getStatus());
            view.put("actionType", task.getActionType());
            view.put("lastFireTime", task.getLastFireTime());
            view.put("triggerCount", task.getTriggerCount());
            view.put("failCount", task.getFailCount());
            return view;
        }
    }

    @Override
    public int pauseByApp(String orgId, String appId) {
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            return coreMapper.pauseByAppId(orgId, appId);
        }
    }

    @Override
    public List<Date> previewNext(String cron, int n) {
        return calculator.previewNext(cron, n);
    }

    // ================================================================
    // 私有工具
    // ================================================================

    private void validate(SystemScheduleTask form) {
        String triggerType = form.getTriggerType();
        if (!ScheduleConstants.TRIGGER_CRON.equals(triggerType)
                && !ScheduleConstants.TRIGGER_FIXED_DELAY.equals(triggerType)
                && !ScheduleConstants.TRIGGER_ONCE.equals(triggerType)) {
            throw new IllegalArgumentException("trigger_type 非法: " + triggerType);
        }
        if (ScheduleConstants.TRIGGER_CRON.equals(triggerType)) {
            calculator.parseCron(form.getCronExpr());
        }
        if (ScheduleConstants.TRIGGER_FIXED_DELAY.equals(triggerType)) {
            if (form.getIntervalSeconds() == null || form.getIntervalSeconds() < 60) {
                throw new IllegalArgumentException("interval_seconds 最小 60 秒");
            }
            if (form.getJitterSeconds() != null && form.getJitterSeconds() > 0) {
                // §7.1：jitter 会等比拉长间隔任务的触发节奏，禁止配置
                form.setJitterSeconds(0);
            }
        }
        if (form.getJitterSeconds() != null && (form.getJitterSeconds() < 0 || form.getJitterSeconds() > 60)) {
            throw new IllegalArgumentException("jitter_seconds 取值 0-60");
        }
        if (ScheduleConstants.TRIGGER_ONCE.equals(triggerType)
                && (form.getNextFireTime() == null || !form.getNextFireTime().after(new Date()))) {
            throw new IllegalArgumentException("一次性任务必须指定未来的触发时间");
        }
        if (actionRegistry.byActionType(form.getActionType()) == null) {
            throw new IllegalArgumentException("未注册的动作类型: " + form.getActionType());
        }
        // P1-7：并发策略取值域校验；三档均已实现（FORBID 保留轮次/SKIP 丢弃并记账/QUEUE 逐轮补跑）
        if (StringUtils.isNotEmpty(form.getConcurrentPolicy())
                && !ScheduleConstants.CONCURRENT_FORBID.equals(form.getConcurrentPolicy())
                && !ScheduleConstants.CONCURRENT_QUEUE.equals(form.getConcurrentPolicy())
                && !ScheduleConstants.CONCURRENT_SKIP.equals(form.getConcurrentPolicy())) {
            throw new IllegalArgumentException("concurrent_policy 取值非法：" + form.getConcurrentPolicy());
        }
        // Groovy 白名单：注册即拒绝（§8.1）
        if ("GROOVY_SCRIPT".equals(form.getActionType()) && form.getActionConfig() != null) {
            String script = JSONUtil.parseObj(form.getActionConfig()).getStr("script");
            if (script != null) {
                sandboxValidator.validate(script);
            }
        }
        if (ScheduleConstants.TRIGGER_CRON.equals(form.getTriggerType())) {
            Long period = calculator.computePeriodSeconds(form.getCronExpr());
            if (period != null && period < 60) {
                throw new IllegalArgumentException("cron 最小触发间隔为 60 秒，当前约 " + period + " 秒");
            }
            form.setPeriodSeconds(period);
        } else if (ScheduleConstants.TRIGGER_FIXED_DELAY.equals(form.getTriggerType()) && form.getIntervalSeconds() != null) {
            form.setPeriodSeconds(form.getIntervalSeconds().longValue());
        } else {
            form.setPeriodSeconds(null);
        }
    }

    private void checkQuota(SystemScheduleTask form) {
        String orgId = StringUtils.isNotEmpty(form.getOrganizationId())
                ? form.getOrganizationId() : SecurityUtils.getOrganizationId();
        if (StringUtils.isEmpty(orgId)) {
            return;
        }
        long orgActive = coreMapper.countActive(orgId, null);
        // P2-6：达 90% 触发应急清理（§11 要求，此前只有告警展示、100% 才硬拒）——
        // 删该组织最老的终态一次性任务，不等 7 天保留期，单次最多 5000 行避免长事务。
        // 注意口径：countActive 只数 READY/PAUSED/TRIGGERED/RUNNING，清终态行**不会**降活跃数，
        // 它 relief 的是任务表体积与扫描/对账压力；活跃数真达上限只能靠用户终止/删除任务。
        if (orgActive >= QUOTA_ORG * 9 / 10) {
            int purged = 0;
            int batch;
            do {
                batch = coreMapper.purgeOldestFinishedByOrg(orgId, 500);
                purged += batch;
            } while (batch >= 500 && purged < 5000);
            if (purged > 0) {
                log.warn("[schedule] 组织 {} 活跃任务 {}/{} 已达 90%，应急清理最老终态任务 {} 条",
                        orgId, orgActive, QUOTA_ORG, purged);
            }
        }
        if (orgActive >= QUOTA_ORG) {
            throw new IllegalArgumentException("组织活跃任务数已达配额上限 " + QUOTA_ORG
                    + "，请先终止或删除不再使用的任务（已完成/已取消的一次性任务不占配额，系统已自动清理）");
        }
        if (StringUtils.isNotEmpty(form.getAppId())) {
            long appActive = coreMapper.countActive(orgId, form.getAppId());
            if (appActive >= QUOTA_APP) {
                throw new IllegalArgumentException("应用活跃任务数已达配额上限 " + QUOTA_APP);
            }
        }
    }

    private SystemScheduleTask buildForInsert(SystemScheduleTask form, String operator) {
        if (StringUtils.isEmpty(form.getSource())) {
            form.setSource(ScheduleConstants.SOURCE_MANUAL);
        }
        if (ScheduleConstants.SOURCE_OPENAPI.equals(form.getSource())
                && !"HTTP_CALL".equals(form.getActionType()) && !"MESSAGE_NOTIFY".equals(form.getActionType())) {
            // §11：OPENAPI 来源只允许 HTTP_CALL/MESSAGE_NOTIFY
            throw new IllegalArgumentException("OPENAPI 来源仅允许 HTTP_CALL/MESSAGE_NOTIFY 动作");
        }
        form.setId(UUID.randomUUID().toString().replace("-", ""));
        form.setTenantId("system-default");
        form.setDepartmentId(form.getDepartmentId() == null ? "" : form.getDepartmentId());
        form.setAppId(form.getAppId() == null ? "" : form.getAppId());
        form.setBizKey(StringUtils.isEmpty(form.getBizKey())
                ? "manual:" + UUID.randomUUID() : form.getBizKey());
        form.setOrganizationId(StringUtils.isNotEmpty(form.getOrganizationId())
                ? form.getOrganizationId() : SecurityUtils.getOrganizationId());
        form.setCreatedBy(operator);
        form.setDeletedFlag("N");
        form.setStatus(ScheduleConstants.STATUS_READY);
        form.setVersion(0L);
        form.setTriggerCount(0L);
        form.setFailCount(0L);
        form.setCurrentAttempt(1);
        form.setDatabaseName(StringUtils.isEmpty(form.getDatabaseName()) ? "master" : form.getDatabaseName());
        form.setMisfirePolicy(StringUtils.isEmpty(form.getMisfirePolicy())
                ? ScheduleConstants.MISFIRE_COMPENSATE : form.getMisfirePolicy());
        form.setMisfireWindowSeconds(form.getMisfireWindowSeconds() == null ? 300 : form.getMisfireWindowSeconds());
        form.setConcurrentPolicy(StringUtils.isEmpty(form.getConcurrentPolicy())
                ? ScheduleConstants.CONCURRENT_FORBID : form.getConcurrentPolicy());
        form.setTimeoutSeconds(form.getTimeoutSeconds() == null ? 300 : Math.min(form.getTimeoutSeconds(), 3600));
        form.setMaxRetry(form.getMaxRetry() == null ? 3 : form.getMaxRetry());
        form.setJitterSeconds(form.getJitterSeconds() == null ? 0 : form.getJitterSeconds());
        form.setNextFireTime(calculator.buildInitialNext(form));
        form.setPendingNextFireTime(null);
        return form;
    }

    private SystemScheduleTask buildForUpdate(SystemScheduleTask form, SystemScheduleTask existing, String operator) {
        // P1-4：字段合并口径与"在途变更应用"共用一份实现（PendingChangeApplier），避免两处漂移
        pendingApplier.applyConfig(form, existing, operator);
        return existing;
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String s) {
            return s;
        }
        return JSONUtil.toJsonStr(obj);
    }

    private String abbreviateKey(String key) {
        return key.length() > 40 ? key.substring(0, 40) : key;
    }

    private long percent(long active, long quota) {
        return quota <= 0 ? 0 : active * 100 / quota;
    }

    private Map<String, Object> result(String id, String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("message", message);
        return map;
    }
}
