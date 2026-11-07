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

package com.privernal.dynamic.advanced.engine;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.dynamic.advanced.domain.DynamicLinkageLog;
import com.privernal.dynamic.advanced.domain.DynamicLinkageRule;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageLogMapper;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 联动规则执行器（§7.5）：幂等日志 → 条件求值 → 有序动作（线程内退避，§7.5.4）→ 状态聚合。
 * <p>
 * 幂等：先插 EXECUTING 占位行（uk_idem 撞键即跳过）再执行动作——进程在执行中崩溃时
 * 重投会撞键，继承事件管道"宁漏不幽灵"取舍（§7.2.1）。
 * 组织上下文显式注入并按快照恢复（不污染调用方事务内的后续事件发布）。
 */
@Slf4j
@Service
public class LinkageExecuteService {

    /** ASYNC 动作失败线程内退避间隔（§7.5.4：只允许一层重试，2s/10s/60s） */
    private static final long[] BACKOFF_MS = {2000L, 10_000L, 60_000L};
    private static final int MAX_ATTEMPTS = BACKOFF_MS.length + 1;

    /** 每组织并发信号量（默认 4，§7.5.5） */
    private static final int PERMIT_PER_ORG = 4;
    private final Map<String, Semaphore> orgPermits = new ConcurrentHashMap<>();

    @Autowired
    private DynamicLinkageLogMapper linkageLogMapper;

    @Autowired
    private ActionHandlerRegistry handlerRegistry;

    @Autowired
    private ObjectProvider<InnerMessageSender> innerMessageSender;

    @Autowired
    private DynamicTableService dynamicTableService;

    /**
     * 执行单条规则。
     *
     * @param async false=SYNC 同事务路径：动作失败向上抛出使业务整体回滚（§11 验收），
     *              且不做线程内退避（同事务里 sleep 只会拉长事务）
     */
    public void runRule(DynamicLinkageRule rule, LinkageEvent event, boolean async) {
        long begin = System.currentTimeMillis();
        String orgId = event.getOrganizationId();
        String oldOrg = OrganizationContextHolder.getOrganizationId();
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        // 必须一次同时忽略组织与数据权限：DataScopeHelper 的 ThreadLocal 是整个 IgnoreStrategy
        // 被 setter 整体替换，setOrgAndIgnoreDepartment 后再有任何 setIgnore 都会把
        // dataPermission 忽略抹掉 → 无会话线程的查询被切面兜底 WHERE 1=4 拒绝
        OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
        OrganizationContextHolder.setOrganizationId(orgId);
        Semaphore permit = orgPermits.computeIfAbsent(orgId, k -> new Semaphore(PERMIT_PER_ORG));
        try {
            permit.acquire();
            try {
                doRun(rule, event, async, begin);
            } finally {
                permit.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("联动执行等待组织信号量被中断 rule={}", rule.getRuleCode());
        } finally {
            restoreOrgContext(oldOrg, oldIgnore);
        }
    }

    private void doRun(DynamicLinkageRule rule, LinkageEvent event, boolean async, long begin) {
        List<String> trace = new ArrayList<>();
        boolean conditionPass = true;
        try {
            // watchFields 过滤（UPDATE 规则只监听关注字段，§7.2）
            String watchSkipReason = watchFieldsSkipReason(rule, event, trace);
            if (watchSkipReason != null) {
                insertFinalLog(rule, event, "N", watchSkipReason, DynamicLinkageLog.STATUS_SKIPPED,
                        "[]", null, begin);
                return;
            }
            ConditionEvaluator.ConditionResult condition =
                    ConditionEvaluator.evaluate(parseObject(rule.getConditionTree()), eventConditionData(event));
            conditionPass = condition.pass();
            trace.add(condition.trace());
            if (!conditionPass) {
                insertFinalLog(rule, event, "N", String.join("；", trace), DynamicLinkageLog.STATUS_SKIPPED,
                        "[]", null, begin);
                return;
            }
        } catch (Exception e) {
            // 条件求值异常（如畸形条件树）按 SKIPPED 软失败，不执行任何动作（§8.2 运行时软失败）
            log.error("联动条件求值异常 rule={}", rule.getRuleCode(), e);
            insertFinalLog(rule, event, "N", "条件求值异常：" + e.getMessage(), DynamicLinkageLog.STATUS_SKIPPED,
                    "[]", null, begin);
            return;
        }

        DynamicLinkageLog logRow = insertExecutingLog(rule, event, String.join("；", trace));

        List<ActionResult> results = new ArrayList<>();
        boolean aborted = false;
        String lastError = null;
        List<ActionConfig> actions = parseActions(rule.getActions());
        for (ActionConfig action : actions) {
            if (aborted) {
                results.add(new ActionResult(action.getType(), ActionResult.STATUS_SKIPPED, 0L,
                        "前序动作中止", 0, null));
                continue;
            }
            ActionResult result = performWithRetry(action, new LinkageContext(rule, event, event.getDepth() + 1), async);
            results.add(result);
            if (ActionResult.STATUS_FAILED.equals(result.getStatus())) {
                lastError = result.getError();
                if (action.isStopOnFail() || "ABORT".equals(rule.getFailPolicy())) {
                    aborted = true;
                }
            }
        }

        String finalStatus = aggregateStatus(results, aborted);
        updateFinalLog(logRow, finalStatus, results, lastError, begin);

 // ASYNC 失败告警规则创建人（站内信）；钉钉/短信告警不在本期（252）
        if (!DynamicLinkageLog.STATUS_SUCCESS.equals(finalStatus)) {
            alertRuleOwner(rule, finalStatus, lastError);
        }
        if (!async && !DynamicLinkageLog.STATUS_SUCCESS.equals(finalStatus)) {
            // SYNC 同事务路径：动作失败 → 业务整体回滚（§11 验收用例）
            throw new ServiceException("联动规则 " + rule.getRuleCode() + " 同步动作失败：" + lastError);
        }
    }

    private ActionResult performWithRetry(ActionConfig action, LinkageContext ctx, boolean async) {
        LinkageActionHandler handler = handlerRegistry.get(action.getType());
        if (handler == null) {
            return ActionResult.failed(action.getType(), "不支持的联动动作类型（未注册处理器）", 0L, 0);
        }
        int maxAttempts = async ? MAX_ATTEMPTS : 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long begin = System.currentTimeMillis();
            try {
                ActionResult result = handler.perform(action, ctx);
                result.setAttempts(attempt);
                return result;
            } catch (Exception e) {
                log.warn("联动动作第 {} 次尝试失败 rule={} action={} error={}",
                        attempt, ctx.displayRule(), action.getType(), e.getMessage(), e);
                if (attempt >= maxAttempts) {
                    return ActionResult.failed(action.getType(),
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                            System.currentTimeMillis() - begin, attempt);
                }
                try {
                    Thread.sleep(BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return ActionResult.failed(action.getType(), "重试等待被中断", System.currentTimeMillis() - begin, attempt);
                }
            }
        }
        return ActionResult.failed(action.getType(), "unreachable", 0L, 0);
    }

    /**
     * watchFields 过滤：配置了 watchFields 的 UPDATE 规则只在关注字段真的变化时触发（§7.2）。
     * 状态回写（EFFECTIVE 双触发路径）不会命中显式 watchFields 的业务字段规则。
     *
     * @return null=继续走条件判定；非 null=跳过原因（记入日志 trace）
     */
    private String watchFieldsSkipReason(DynamicLinkageRule rule, LinkageEvent event,
                                         List<String> trace) {
        if (!DynamicLinkageRule.EVENT_UPDATE.equals(event.getEventType())) {
            return null;
        }
        JSONObject eventConfig = parseObject(rule.getEventConfig());
        if (eventConfig == null) {
            return null;
        }
        List<String> watchFields = eventConfig.getList("watchFields", String.class);
        if (watchFields == null || watchFields.isEmpty()) {
            return null;
        }
        List<String> changed = new ArrayList<>();
        for (String field : watchFields) {
            Object before = normalize(ConditionEvaluator.lookup(event.getBefore(), field));
            Object after = normalize(ConditionEvaluator.lookup(event.getAfter(), field));
            if (!ConditionEvaluator.valuesEqual(before, after)) {
                changed.add(field);
            }
        }
        if (changed.isEmpty()) {
            return "watchFields " + watchFields + " 均未变化，跳过（before/after 对比）";
        }
        trace.add("watchFields 变化：" + changed);
        return null;
    }

    private Object normalize(Object value) {
        if (value instanceof String s && s.isEmpty()) {
            return null;
        }
        return value;
    }

    /**
     * 幂等占位：先插 EXECUTING 行，撞 uk_idem 即说明同事件已执行/在执行（DUPLICATE 语义）
     */
    private DynamicLinkageLog insertExecutingLog(DynamicLinkageRule rule, LinkageEvent event, String conditionTrace) {
        DynamicLinkageLog row = newLog(rule, event, "Y", conditionTrace);
        row.setStatus(DynamicLinkageLog.STATUS_EXECUTING);
        row.setErrorMsg("EXECUTING");
        LinkageSupport.assertLinkageIgnoreScope();
        linkageLogMapper.insert(row);
        return row;
    }

    private void insertFinalLog(DynamicLinkageRule rule, LinkageEvent event, String conditionPass, String trace,
                                String status, String actionResults, String errorMsg, long begin) {
        DynamicLinkageLog row = newLog(rule, event, conditionPass, trace);
        row.setStatus(status);
        row.setActionResults(actionResults);
        row.setErrorMsg(errorMsg);
        row.setStartTime(new Date(begin));
        row.setCostMs((int) (System.currentTimeMillis() - begin));
        try {
            LinkageSupport.assertLinkageIgnoreScope();
            linkageLogMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.info("联动日志幂等撞键（视为已执行）rule={} eventId={}", rule.getRuleCode(), event.getEventId());
        }
    }

    private void updateFinalLog(DynamicLinkageLog row, String status, List<ActionResult> results,
                                String errorMsg, long begin) {
        LinkageSupport.assertLinkageIgnoreScope();
        linkageLogMapper.finalizeLog(row.getId(), status, JSON.toJSONString(results), errorMsg,
                new Date(begin), (int) (System.currentTimeMillis() - begin));
    }

    private DynamicLinkageLog newLog(DynamicLinkageRule rule, LinkageEvent event, String conditionPass, String trace) {
        DynamicLinkageLog row = new DynamicLinkageLog();
        row.setRuleId(rule.getId());
        row.setFormId(rule.getFormId());
        row.setRuleCode(rule.getRuleCode());
        row.setOrganizationId(event.getOrganizationId());
        row.setDatabaseName(event.getDatabaseName());
        row.setEventType(event.getEventType());
        row.setDataId(event.getDataId());
        row.setEventId(event.getEventId());
        row.setSourceTag(event.getSourceTag());
        row.setDepth(event.getDepth());
        row.setFireTime(event.getFireTime());
        row.setConditionPass(conditionPass);
        row.setConditionTrace(trace);
        row.setStartTime(new Date());
        return row;
    }

    private String aggregateStatus(List<ActionResult> results, boolean aborted) {
        if (results.isEmpty()) {
            return DynamicLinkageLog.STATUS_SUCCESS;
        }
        long failed = results.stream().filter(r -> ActionResult.STATUS_FAILED.equals(r.getStatus())).count();
        if (failed == 0) {
            return DynamicLinkageLog.STATUS_SUCCESS;
        }
        if (aborted || failed == results.size()) {
            return DynamicLinkageLog.STATUS_FAILED;
        }
        return DynamicLinkageLog.STATUS_PARTIAL;
    }

    private void alertRuleOwner(DynamicLinkageRule rule, String status, String error) {
        try {
            InnerMessageSender sender = innerMessageSender.getIfAvailable();
            if (sender == null || StringUtils.isEmpty(rule.getCreatedBy())) {
                return;
            }
            sender.send(List.of(rule.getCreatedBy()),
                    "联动规则执行" + ("FAILED".equals(status) ? "失败" : "部分失败"),
                    "规则：" + rule.getName() + "（" + rule.getRuleCode() + "）\n状态：" + status
                            + (StringUtils.isEmpty(error) ? "" : "\n原因：" + error));
        } catch (Exception e) {
            log.warn("联动失败告警发送失败 rule={}", rule.getRuleCode(), e);
        }
    }

    private Map<String, Object> eventConditionData(LinkageEvent event) {
        return "DELETE".equals(event.getEventType()) || event.getAfter() == null
                ? event.getBefore() : event.getAfter();
    }

    private JSONObject parseObject(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        return JSON.parseObject(json);
    }

    private List<ActionConfig> parseActions(String actions) {
        List<ActionConfig> list = JSON.parseArray(actions, ActionConfig.class);
        return list == null ? List.of() : list;
    }

    private void restoreOrgContext(String oldOrg, Boolean oldIgnore) {
        if (oldOrg == null) {
            OrganizationContextHolder.clear();
        } else {
            OrganizationContextHolder.setOrganizationId(oldOrg);
            OrganizationContextHolder.setIgnore(Boolean.TRUE.equals(oldIgnore));
        }
    }
}
