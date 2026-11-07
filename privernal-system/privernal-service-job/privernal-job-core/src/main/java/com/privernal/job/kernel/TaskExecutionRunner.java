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
import cn.hutool.json.JSONUtil;
import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.action.ScheduleActionRegistry;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.context.ScheduleContextRestorer;
import com.privernal.job.domain.SystemScheduleTask;
import com.privernal.job.domain.SystemScheduleTaskLog;
import com.privernal.job.mapper.ScheduleLogMapper;
import com.privernal.job.mapper.ScheduleTaskCoreMapper;
import com.privernal.system.http.OutboundRequest;
import com.privernal.system.http.OutboundResponse;
import com.privernal.message.constant.MessageConstant;
import com.privernal.message.dto.SystemMessageDTO;
import com.privernal.message.dto.SystemMessageUserDTO;
import com.privernal.message.service.ISystemMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 执行链（顺序：CAS 抢占 → 投池 → 执行线程内 INSERT 日志行(uk_idem 幂等)
 * → UPDATE RUNNING → 动作 → 终态回写）。
 *
 * <p>池满回退发生在本类之前的投递环节（ScheduleKernel.dispatch），保证被拒路径
 * 不占用幂等键（§8.2 前置约束——否则 30s 后重投同一 fire_time 必撞唯一键被误判"正在执行"）。</p>
 *
 * <p>回写规则（§7.5）：执行线程按 status='RUNNING' AND running_instance=? 条件写；
 * 对账不触碰未超时的 RUNNING 行，版本对齐由本类完成。周期任务失败分档（§7.7）：
 * period >= 86400 沿用原 fire_time 补跑一次（attempt+1，只补一次）；&lt; 86400 不补、next=pending。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
public class TaskExecutionRunner {

    @Autowired
    private ScheduleTaskCoreMapper coreMapper;

    @Autowired
    private ScheduleLogMapper logMapper;

    @Autowired
    private ScheduleActionRegistry actionRegistry;

    @Autowired
    private ScheduleContextRestorer contextRestorer;

    @Autowired
    private TriggerCalculator calculator;

    @Autowired
    private ISystemMessageService systemMessageService;

    @Autowired
    private com.privernal.system.service.ISystemUserService systemUserService;

    @Autowired
    private ScheduleInstanceId instanceId;

    @Autowired
    private com.privernal.system.http.HttpOutboundClient outboundClient;

    @Autowired
    private com.privernal.job.config.ScheduleMetrics metrics;

    @Autowired
    private PendingChangeApplier pendingApplier;

    /**
     * 调度触发的执行（任务行已被 CAS 为 TRIGGERED，本方法在执行池线程内运行）。
     *
     * @param task     CAS 成功后的任务行快照
     * @param fireTime 本轮计划触发时间（= 行的 next_fire_time，幂等键组成部分）
     */
    public void runScheduled(SystemScheduleTask task, Date fireTime) {
        int attempt = task.getCurrentAttempt() == null ? 1 : task.getCurrentAttempt();
        SystemScheduleTaskLog logRow = insertRunningLog(task, fireTime, attempt, null);
        if (logRow == null) {
            // 幂等冲突：本轮已被执行/正在执行（双主/重复投递），放弃；任务行留在 TRIGGERED 由对账按租约回收
            log.warn("[schedule] idempotent conflict, skip execution taskId={} fireTime={} attempt={}",
                    task.getId(), fireTime, attempt);
            return;
        }
        if (coreMapper.markRunning(task.getId(), instanceId.getInstanceId()) == 0) {
            // TRIGGERED 已被对账回收（租约过期）——本轮放弃，日志行置 CANCELLED
            logMapper.updateLogResult(logRow.getId(), ScheduleConstants.LOG_CANCELLED, null, null, "lease reclaimed before running", null);
            return;
        }
        executeAndFinish(task, fireTime, attempt, logRow, false);
        // P1-4：本轮已落回 READY/PAUSED（或进入终态），把这一轮期间登记的在途变更应用掉
        applyPendingQuietly(task.getId());
    }

    /** 在途变更应用失败不影响本轮执行结果，下一轮回写或对账时重试 */
    private void applyPendingQuietly(String taskId) {
        try {
            pendingApplier.apply(taskId);
        } catch (Exception e) {
            log.warn("[schedule] 在途变更应用失败 taskId={}", taskId, e);
        }
    }

    /**
     * 手动执行（§11）：不改任务行状态（周期任务保持 READY/PAUSED），
     * 互斥由 casManualRun 的 running_instance 占位保证；fire_time=now（毫秒级不同，
     * 幂等键天然不冲突），attempt=1，source 保留原值。
     */
    public void runManual(SystemScheduleTask task) {
        Date fireTime = new Date();
        SystemScheduleTaskLog logRow = insertRunningLog(task, fireTime, 1, "manual");
        if (logRow == null) {
            log.warn("[schedule] manual run idempotent conflict taskId={}", task.getId());
            return;
        }
        try {
            executeAndFinish(task, fireTime, 1, logRow, true);
        } catch (Throwable t) {
            log.error("[schedule] manual run crashed taskId={}", task.getId(), t);
        } finally {
            coreMapper.releaseManualRun(task.getId(), instanceId.getInstanceId());
            // P1-4：手动执行也占用过这一行，顺手应用登记的在途变更
            applyPendingQuietly(task.getId());
        }
    }

    /** 核心执行段：动作 → 日志终态 → 任务行回写（调度链专用，手动链只写日志不动状态机） */
    private void executeAndFinish(SystemScheduleTask task, Date fireTime, int attempt,
                                  SystemScheduleTaskLog logRow, boolean manual) {
        String instance = instanceId.getInstanceId();
        long start = System.currentTimeMillis();
        boolean success;
        String digest;
        String error;
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enter(task)) {
            ActionResult result = performAction(task, logRow);
            success = result.isSuccess();
            digest = result.getDigest();
            error = result.getError();
        } catch (Throwable t) {
            log.error("[schedule] execution crashed taskId={}", task.getId(), t);
            success = false;
            digest = null;
            error = t.getMessage();
        }
        int cost = (int) (System.currentTimeMillis() - start);
        logMapper.updateLogResult(logRow.getId(),
                success ? ScheduleConstants.LOG_SUCCESS : ScheduleConstants.LOG_FAILED,
                null, cost, success ? null : abbreviate(error), success ? abbreviate(digest) : null);
        metrics.execution(success ? "success" : "failed");

        if (manual) {
            return;
        }
        boolean once = ScheduleConstants.TRIGGER_ONCE.equals(task.getTriggerType())
                || ScheduleConstants.SOURCE_WORKFLOW.equals(task.getSource());
        if (success) {
            if (once) {
                coreMapper.finishOnce(task.getId(), instance, fireTime);
                // OPENAPI 终态回调（§8.5）：一次性任务成功即终态，POST 一次不重试
                fireOpenapiCallback(task, true, success ? digest : null);
            } else {
                finishCycle(task, instance, fireTime);
            }
            return;
        }
        // ===== 失败处置（§7.7 分档）=====
        if (once) {
            int maxRetry = task.getMaxRetry() == null ? 3 : task.getMaxRetry();
            if (attempt <= maxRetry) {
                long backoff = calculator.retryBackoffSeconds(attempt);
                Date retryAt = new Date(System.currentTimeMillis() + backoff * 1000L);
                coreMapper.markFailed(task.getId(), instance, retryAt, task.getPendingNextFireTime(),
                        attempt + 1, false, true, abbreviate(error));
                log.info("[schedule] ONCE task failed, retry in {}s taskId={} attempt={}",
                        backoff, task.getId(), attempt);
            } else {
                coreMapper.markFailed(task.getId(), instance, fireTime, null, attempt, true, true, abbreviate(error));
                log.warn("[schedule] ONCE task retries exhausted taskId={} attempts={}", task.getId(), attempt);
                // 重试耗尽也是终态：回调失败结果（§8.5"成功或重试耗尽"）
                fireOpenapiCallback(task, false, error);
            }
        } else {
            Long period = task.getPeriodSeconds();
            Date pending = task.getPendingNextFireTime();
            // P1-6：current_attempt>1 表示本轮已是补偿执行——补跑只补一次，不再无限重复；
            // 沿用原 fire_time 使幂等键可预测，attempt+1 让补跑轮能落到新的 uk_idem 行
            boolean compensated = attempt > 1;
            boolean daily = period != null && period >= ScheduleConstants.DAILY_PERIOD_SECONDS;
            Date next;
            int nextAttempt;
            if (daily && !compensated) {
                next = fireTime != null ? fireTime : new Date(System.currentTimeMillis() + 60_000L);
                nextAttempt = attempt + 1;
                // P1-8：只有"确实被丢弃的轮次"才记账（补跑占用的是原轮次，pending 轮次不再追）
                if (pending != null) {
                    writeSkipLog(task, pending, ScheduleConstants.LOG_SKIPPED_CONCURRENT,
                            "失败补跑占用原轮次，该轮次到点被跳过");
                }
            } else {
                // 小时级及以下：不补，等下个周期（next=pending，相位不漂）
                next = pending != null ? pending : calculator.nextCycleFireTime(task, fireTime);
                nextAttempt = 1;
            }
            // P1-6：仅非补跑轮的首次失败累加 fail_count（补跑失败不重复计，避免撞 10 次自动暂停；
            // 首跑失败必须计，否则周期任务的自动暂停保护永不生效）
            coreMapper.markFailed(task.getId(), instance, next, pending, nextAttempt,
                    false, !compensated, abbreviate(error));
            // 连续失败 10 次自动暂停 + 站内信告警（§7.7）
            if (coreMapper.autoPauseIfFailed(task.getId(), ScheduleConstants.FAIL_PAUSE_THRESHOLD) > 0) {
                notifyAutoPaused(task);
            }
        }
    }

    /** 周期任务成功回写（§7.4：FORBID 挡住的轮次在此记账） */
    private void finishCycle(SystemScheduleTask task, String instance, Date fireTime) {
        Date pending = task.getPendingNextFireTime();
        Date now = new Date();
        if (pending == null) {
            pending = calculator.nextCycleFireTime(task, fireTime);
        }
        if (pending != null && pending.before(now)) {
            // 执行期间有轮次到点（扫描条件 status=READY，RUNNING 行不可见，§7.4）
            if (ScheduleConstants.CONCURRENT_QUEUE.equals(task.getConcurrentPolicy())) {
                // QUEUE（§7.4 P2）：错过轮次落库排队逐轮补跑，不丢不并行。
                // 枚举 pending→now 之间的理论触发点逐个入队（INSERT IGNORE 幂等，cap 100 防爆），
                // next=最早队行（即 pending），消费队首；后续回写继续消费余量。
                Date t = pending;
                int enqueued = 0;
                while (t != null && !t.after(now) && enqueued < 100) {
                    coreMapper.enqueueQueue(task.getId(), t);
                    t = calculator.nextCycleFireTime(task, t);
                    enqueued++;
                }
                Date earliest = coreMapper.pollEarliestQueue(task.getId());
                Date queueNext = earliest != null ? earliest : calculator.nextCycleFireTime(task, now);
                if (earliest != null) {
                    coreMapper.consumeQueue(task.getId(), earliest);
                }
                if (enqueued > 0) {
                    metrics.queueEnqueued(enqueued);
                    log.info("[schedule] QUEUE task {} enqueued {} missed rounds", task.getId(), enqueued);
                }
                coreMapper.finishCycle(task.getId(), instance, fireTime, queueNext,
                        queueNext == null ? null : calculator.nextCycleFireTime(task, queueNext));
                return;
            }
            // P1-7：concurrent_policy=SKIP 与 FORBID 语义分家——SKIP 丢弃被挡轮次（相位从 now 推进）
            // 并记账；FORBID 保留轮次（next=pending 立即重捕、不移动相位）。misfire_policy=SKIP 同走丢弃。
            if (ScheduleConstants.CONCURRENT_SKIP.equals(task.getConcurrentPolicy())
                    || ScheduleConstants.MISFIRE_SKIP.equals(task.getMisfirePolicy())) {
                writeSkipLog(task, pending, ScheduleConstants.LOG_SKIPPED_CONCURRENT, "上一轮执行期间本轮到点被跳过");
                Date next = calculator.nextCycleFireTime(task, now);
                coreMapper.finishCycle(task.getId(), instance, fireTime, next,
                        next == null ? null : calculator.nextCycleFireTime(task, next));
            } else {
                // COMPENSATE：立即补一轮且不移动相位（§7.4）——next=pending（被挡轮次的原定时刻，
                // 已过期会被下轮扫描立即重捕，补跑行的 fire_time 沿用原定时刻）；pendingNext 按
                // 原相位从 pending 推进，不从 now 重算（否则相位游走，破坏 §7.7 的相位口径）
                coreMapper.finishCycle(task.getId(), instance, fireTime, pending,
                        calculator.nextCycleFireTime(task, pending));
                log.info("[schedule] cycle compensate taskId={} blocked round at {}", task.getId(), pending);
            }
            return;
        }
        coreMapper.finishCycle(task.getId(), instance, fireTime, pending,
                pending == null ? null : calculator.nextCycleFireTime(task, pending));
    }

    /**
     * OPENAPI 终态回调（§8.5）：仅 OPENAPI 来源、一次性任务进入终态（成功或重试耗尽）时
     * POST 一次，失败不二次回调。传输走 §8.4 公共出站栈（D1-B 落地），URL 来自注册方自己。
     */
    private void fireOpenapiCallback(SystemScheduleTask task, boolean ok, String message) {
        String callbackUrl = task.getCallbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()
                || !ScheduleConstants.SOURCE_OPENAPI.equals(task.getSource())) {
            return;
        }
        callbackExecutor.submit(() -> {
            try {
                OutboundRequest req = new OutboundRequest();
                req.setUrl(callbackUrl);
                req.setTimeoutMs(10_000);
                req.getHeaders().put("Content-Type", "application/json");
                req.setBody("{\"taskId\":\"" + task.getId() + "\",\"bizKey\":\"" + task.getBizKey()
                        + "\",\"success\":" + ok + ",\"message\":"
                        + cn.hutool.json.JSONUtil.quote(message == null ? "" : abbreviate(message)) + "}");
                OutboundResponse resp = outboundClient.send(req);
                log.info("[schedule] openapi callback taskId={} url={} -> HTTP {}",
                        task.getId(), callbackUrl, resp.isExecuted() ? resp.getStatusCode() : resp.getError());
            } catch (Exception e) {
                log.warn("[schedule] openapi callback failed (no retry) taskId={} url={}: {}",
                        task.getId(), callbackUrl, e.getMessage());
            }
        });
    }

    private static final java.util.concurrent.ExecutorService callbackExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "schedule-callback");
                thread.setDaemon(true);
                return thread;
            });

    /** 动作执行（上下文已装配） */
    private ActionResult performAction(SystemScheduleTask task, SystemScheduleTaskLog logRow) {
        ScheduleActionHandler handler = actionRegistry.byActionType(task.getActionType());
        if (handler == null) {
            return ActionResult.fail("未注册的动作类型: " + task.getActionType());
        }
        Map<String, Object> config = parseConfig(task.getActionConfig());
        String database;
        if ("MESSAGE_NOTIFY".equals(task.getActionType()) || "HTTP_CALL".equals(task.getActionType())
                || "BEAN".equals(task.getActionType())) {
            database = contextRestorer.defaultDatabase();
        } else {
            database = contextRestorer.resolveDatabase(task);
        }
        TaskExecutionContext context = new TaskExecutionContext(config, database);
        return handler.doAction(toSnapshot(task), context);
    }

    /** 幂等抢占：INSERT IGNORE，冲突返回 null（§7.6） */
    private SystemScheduleTaskLog insertRunningLog(SystemScheduleTask task, Date fireTime, int attempt, String tag) {
        SystemScheduleTaskLog logRow = new SystemScheduleTaskLog();
        logRow.setTaskId(task.getId());
        logRow.setOrganizationId(task.getOrganizationId());
        logRow.setAppId(task.getAppId());
        logRow.setSource(task.getSource());
        logRow.setFireTime(fireTime);
        logRow.setAttempt(attempt);
        logRow.setTaskVersion(task.getVersion());
        logRow.setActionSnapshot(task.getActionConfig());
        logRow.setExecutor(instanceId.getInstanceId() + (tag == null ? "" : ":" + tag));
        try {
            if (coreMapper.insertLogIgnore(logRow) == 0) {
                // 幂等冲突：本轮已被执行/正在执行，放弃（P1-3：改显式 INSERT 后仅 1062 走这里，
                // 无分区/字段截断等真实错误向上抛，任务留 TRIGGERED 由租约回收重试）
                return null;
            }
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            return null;
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            Throwable root = dive;
            while (root.getCause() != null) { root = root.getCause(); }
            if (root instanceof java.sql.SQLIntegrityConstraintViolationException) {
                return null;
            }
            throw dive;
        }
        // INSERT 不回填自增 id，按唯一键反查
        return logRow.getId() == null ? findByUniqueId(task.getId(), fireTime, attempt) : logRow;
    }

    private SystemScheduleTaskLog findByUniqueId(String taskId, Date fireTime, int attempt) {
        return logMapper.selectLogByUniqueId(taskId, formatFireTime(fireTime), attempt);
    }

    private String formatFireTime(Date fireTime) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(fireTime);
    }


    /** 跳过类日志行：attempt 固定 0，与执行链不撞 uk_idem（§7.2 attempt 约定） */
    public void writeSkipLog(SystemScheduleTask task, Date fireTime, String status, String reason) {
        SystemScheduleTaskLog logRow = new SystemScheduleTaskLog();
        logRow.setTaskId(task.getId());
        logRow.setOrganizationId(task.getOrganizationId());
        logRow.setAppId(task.getAppId());
        logRow.setSource(task.getSource());
        logRow.setFireTime(fireTime);
        logRow.setAttempt(0);
        logRow.setTaskVersion(task.getVersion());
        logRow.setActionSnapshot(task.getActionConfig());
        logRow.setExecutor(instanceId.getInstanceId());
        try {
            coreMapper.insertLogIgnore(logRow);
            SystemScheduleTaskLog inserted = findByUniqueId(task.getId(), fireTime, 0);
            if (inserted != null && inserted.getId() != null) {
                logMapper.updateLogResult(inserted.getId(), status, null, 0, reason, null);
            }
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 跳过行已存在（同一次触发被补偿/重放写入过），视为成功
        } catch (Exception e) {
            log.warn("[schedule] write skip log failed taskId={} status={}", task.getId(), status, e);
        }
    }

    /** 连续失败自动暂停的站内信告警（§7.7；只走站内信，钉钉无发送能力，§15）。
     *  P2-7：收件人从"仅 created_by"扩到该组织的组织管理员（role_key=SuperRole）——
     *  代理用户/系统注册的任务 created_by 常是 system，原来等于无人知晓。 */
    private void notifyAutoPaused(SystemScheduleTask task) {
        try {
            java.util.Set<String> receivers = new java.util.LinkedHashSet<>();
            if (task.getCreatedBy() != null && !task.getCreatedBy().isBlank()) {
                receivers.add(task.getCreatedBy());
            }
            try {
                List<com.privernal.system.dto.SystemUserDTO> admins = systemUserService
                        .selectUserDTOByRoleCodeList(ScheduleConstants.ROLE_ORG_ADMIN, null, task.getOrganizationId());
                if (admins != null) {
                    admins.stream().map(com.privernal.system.dto.SystemUserDTO::getId)
                            .filter(id -> id != null && !id.isBlank())
                            .limit(200)
                            .forEach(receivers::add);
                }
            } catch (Exception e) {
                log.warn("[schedule] 组织管理员收件人解析失败 taskId={}，退化为只发创建人", task.getId(), e);
            }
            if (receivers.isEmpty()) {
                log.warn("[schedule] 自动暂停告警无可用收件人 taskId={}", task.getId());
                return;
            }
            SystemMessageDTO message = new SystemMessageDTO();
            message.setMainTitle("定时任务已自动暂停");
            message.setSubTitle("定时任务已自动暂停");
            message.setMessageType(MessageConstant.MESSAGE_TYPE_ALARM);
            message.setContent("任务「" + task.getTitle() + "」连续失败 " + ScheduleConstants.FAIL_PAUSE_THRESHOLD
                    + " 次已自动暂停，最近错误：" + task.getLastError());
            message.setCreatedName(MessageConstant.CREATED_DEFAULT);
            message.setOrganizationId(task.getOrganizationId());
            List<SystemMessageUserDTO> userList = new java.util.ArrayList<>();
            for (String userId : receivers) {
                SystemMessageUserDTO receiver = new SystemMessageUserDTO();
                receiver.setUserId(userId);
                userList.add(receiver);
            }
            message.setUserList(userList);
            systemMessageService.saveSystemMessage(message);
        } catch (Exception e) {
            log.error("[schedule] auto-pause notify failed taskId={}", task.getId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String actionConfig) {
        if (actionConfig == null || actionConfig.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            JSONObject obj = JSONUtil.parseObj(actionConfig);
            return (Map<String, Object>) (Map<String, ?>) obj;
        } catch (Exception e) {
            log.warn("[schedule] action config parse failed: {}", actionConfig, e);
            return Collections.emptyMap();
        }
    }

    /** 内核实体 → 动作 SPI 快照 */
    private com.privernal.job.api.dto.ScheduleTaskSnapshot toSnapshot(SystemScheduleTask task) {
        com.privernal.job.api.dto.ScheduleTaskSnapshot snapshot = new com.privernal.job.api.dto.ScheduleTaskSnapshot();
        snapshot.setId(task.getId());
        snapshot.setOrganizationId(task.getOrganizationId());
        snapshot.setAppId(task.getAppId());
        snapshot.setSource(task.getSource());
        snapshot.setBizKey(task.getBizKey());
        snapshot.setTitle(task.getTitle());
        snapshot.setTriggerType(task.getTriggerType());
        snapshot.setCronExpr(task.getCronExpr());
        snapshot.setTimeoutSeconds(task.getTimeoutSeconds());
        snapshot.setMaxRetry(task.getMaxRetry());
        snapshot.setActionType(task.getActionType());
        snapshot.setActionConfig(task.getActionConfig());
        snapshot.setBizContext(task.getBizContext());
        snapshot.setDatabaseName(task.getDatabaseName());
        snapshot.setNextFireTime(task.getNextFireTime());
        snapshot.setVersion(task.getVersion());
        return snapshot;
    }

    private String abbreviate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1900 ? s.substring(0, 1900) : s;
    }
}
