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

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.advanced.domain.DynamicLinkageRule;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 联动分发器（§7.5.2 两条分发路径）：
 * <ul>
 *   <li>ASYNC：业务提交后由 {@code DataChangeEventSubscriber}（内部监听器）进入，
 *       丢到独立执行池，失败不影响业务（宁漏不幽灵）；</li>
 *   <li>SYNC：业务事务内由 DynamicTableServiceImpl 收敛点直调，动作失败整体回滚。</li>
 * </ul>
 * 两条路径共用同一套规则匹配、depth 短路与执行语义。
 */
@Slf4j
@Component
public class LinkageDispatcher {

    @Autowired
    private DynamicLinkageRuleMapper ruleMapper;

    @Autowired
    private LinkageExecuteService executeService;

    @Autowired
    private LinkageExecutorPool executorPool;

    @Autowired
    private LinkageSupport linkageSupport;

    @Autowired
    private com.privernal.dynamic.dbs.service.DynamicTableService dynamicTableService;

    @Autowired
    private AdvancedKillSwitch killSwitch;

    /** ASYNC 分发：交给独立执行池（afterCommit 线程不阻塞） */
    public void dispatchAsync(LinkageEvent event) {
        List<DynamicLinkageRule> rules = matchRules(event);
        if (rules.isEmpty()) {
            return;
        }
        for (DynamicLinkageRule rule : rules) {
            if (isSyncMode(rule)) {
                // SYNC 规则已在业务事务内直调过，这里不重复执行
                continue;
            }
            if (depthExceeded(rule, event)) {
                log.info("联动规则 {} 命中递归深度上限（事件深度 {} >= maxDepth {}），跳过",
                        rule.getRuleCode(), event.getDepth(), rule.getMaxDepth());
                continue;
            }
            executorPool.submit(() -> {
                try {
                    executeService.runRule(rule, event, true);
                } catch (Exception e) {
                    log.error("联动规则异步执行失败 rule={} eventId={}", rule.getRuleCode(), event.getEventId(), e);
                }
            });
        }
    }

    /**
     * TIMER 到点触发（§7.7 调度内核回调，actionType=LINKAGE_TIMER）：
     * WHOLE_TABLE 整表一次（不评估行条件）；PER_ROW 逐条（单次最多 max_rows_per_fire 行，
     * 超出截断并告警）。event_id=sha1(ruleId|fireTime)，与调度重试同构（§7.5.1）。
     */
    public void dispatchTimerFire(String ruleId, java.util.Date fireTime, String organizationId, String databaseName) {
        if (!killSwitch.linkageEnabled()) {
            log.info("联动能力已被平台关停（kill switch），跳过 TIMER 触发 ruleId={}", ruleId);
            return;
        }
        DynamicLinkageRule rule = ruleMapper.selectById(ruleId);
        if (rule == null || !"Y".equals(rule.getEnabled())
                || !DynamicLinkageRule.EVENT_TIMER.equals(rule.getEventType())) {
            log.info("TIMER 任务对应的规则不存在/停用/类型不符，跳过 ruleId={}", ruleId);
            return;
        }
        com.privernal.dynamic.dbs.domain.DynamicForm form = linkageSupport.loadForm(rule.getFormId());
        if (form == null || StringUtils.isEmpty(form.getTableCode())) {
            log.warn("TIMER 规则关联表单不存在，跳过 ruleId={}", ruleId);
            return;
        }
        String eventId = LinkageIdemUtils.sha1("TIMER|" + ruleId + "|"
                + (fireTime == null ? 0L : fireTime.getTime()));
        LinkageEvent base = LinkageEvent.of(DynamicLinkageRule.EVENT_TIMER, form.getTableCode(), null, null, null);
        base.setFormId(form.getId());
        base.setOrganizationId(organizationId);
        base.setDatabaseName(StringUtils.isEmpty(databaseName) ? null : databaseName);
        base.setSourceTag(DataWriteAttributes.SOURCE_NORMAL);
        base.setDepth(0);
        base.setEventId(eventId);
        base.setFireTime(fireTime);
        base.setOccurredAt(new java.util.Date());

        boolean perRow = !"WHOLE_TABLE".equals(rule.getTargetMode());
        if (!perRow) {
            // 整表一次：无行上下文，条件树应为恒真；配了行条件按"无值不命中"自然跳过并在 trace 说明
            executorPool.submit(() -> {
                try {
                    executeService.runRule(rule, base, true);
                } catch (Exception e) {
                    log.error("TIMER 联动整表执行失败 rule={}", rule.getRuleCode(), e);
                }
            });
            return;
        }
        int limit = rule.getMaxRowsPerFire() == null ? 5000 : rule.getMaxRowsPerFire();
        java.util.function.BiConsumer<String, java.util.Map<String, Object>> fireOne = (dataId, row) -> {
            LinkageEvent rowEvent = LinkageEvent.of(DynamicLinkageRule.EVENT_TIMER, base.getTableCode(),
                    dataId, null, row);
            rowEvent.setOrganizationId(base.getOrganizationId());
            rowEvent.setDatabaseName(base.getDatabaseName());
            rowEvent.setSourceTag(base.getSourceTag());
            rowEvent.setDepth(0);
            // 每行的 event_id 追加 dataId，保证同轮多行互不撞幂等键
            rowEvent.setEventId(LinkageIdemUtils.sha1(eventId + "|" + dataId));
            rowEvent.setFireTime(fireTime);
            rowEvent.setOccurredAt(base.getOccurredAt());
            executorPool.submit(() -> {
                try {
                    executeService.runRule(rule, rowEvent, true);
                } catch (Exception e) {
                    log.error("TIMER 联动逐行执行失败 rule={} dataId={}", rule.getRuleCode(), dataId, e);
                }
            });
        };
        linkageSupport.fireTimerRows(rule, base, limit, fireOne);
    }

    /**
 * DATE_FIELD 数据到期到点触发（DESIGNER_DATA / high-setting ，
     * actionType=LINKAGE_DATE_FIRE）：对注册时快照的那一行执行规则动作。
     * event_id=sha1(ruleId|dataId|fireTime)——数据改期后同 dataId 可再次触发。
     * 数据已删（行查不到）时静默跳过：删除级联取消（DateExpiryRegistrar.cancelForDeletedRow 精确取消）是主链路，此处兜底。
     */
    public void dispatchDateFire(String ruleId, String dataId, java.util.Date fireTime,
                                 String organizationId, String databaseName) {
        // 联动能力总开关（kill switch）关停时静默跳过，与 TIMER/即时事件同口径
        if (!killSwitch.linkageEnabled()) {
            log.info("联动能力已被平台关停（kill switch），跳过 DATE_FIELD 触发 ruleId={} dataId={}", ruleId, dataId);
            return;
        }
        DynamicLinkageRule rule = ruleMapper.selectById(ruleId);
        if (rule == null || !"Y".equals(rule.getEnabled())
                || !DynamicLinkageRule.EVENT_DATE_FIELD.equals(rule.getEventType())) {
            log.info("DATE_FIELD 任务对应的规则不存在/停用/类型不符，跳过 ruleId={} dataId={}", ruleId, dataId);
            return;
        }
        com.privernal.dynamic.dbs.domain.DynamicForm form = linkageSupport.loadForm(rule.getFormId());
        if (form == null || StringUtils.isEmpty(form.getTableCode())) {
            log.warn("DATE_FIELD 规则关联表单不存在，跳过 ruleId={} dataId={}", ruleId, dataId);
            return;
        }
        String database = StringUtils.isEmpty(databaseName)
                ? linkageSupport.resolveDatabase(organizationId) : databaseName;
        java.util.Map<String, Object> rowData = null;
        try {
            rowData = dynamicTableService.findById(database, form.getTableCode(), dataId);
        } catch (Exception e) {
            log.warn("DATE_FIELD 规则 {} 读行失败 dataId={}", rule.getRuleCode(), dataId, e);
        }
        if (rowData == null || rowData.isEmpty()) {
            log.info("DATE_FIELD 到点但数据已不存在，跳过（删除级联取消应已先行） rule={} dataId={}",
                    rule.getRuleCode(), dataId);
            return;
        }
        String eventId = LinkageIdemUtils.sha1("DATE|" + ruleId + "|" + dataId + "|"
                + (fireTime == null ? 0L : fireTime.getTime()));
        LinkageEvent event = LinkageEvent.of(DynamicLinkageRule.EVENT_DATE_FIELD, form.getTableCode(),
                dataId, null, rowData);
        event.setFormId(form.getId());
        event.setOrganizationId(organizationId);
        event.setDatabaseName(database);
        event.setSourceTag(DataWriteAttributes.SOURCE_NORMAL);
        event.setDepth(0);
        event.setEventId(eventId);
        event.setFireTime(fireTime);
        event.setOccurredAt(new java.util.Date());
        LinkageSupport.assertLinkageIgnoreScope();
        executorPool.submit(() -> {
            try {
                executeService.runRule(rule, event, true);
            } catch (Exception e) {
                log.error("DATE_FIELD 联动执行失败 rule={} dataId={}", rule.getRuleCode(), dataId, e);
            }
        });
    }

    /**
     * SYNC 分发（§7.5.2）：业务事务内直调，动作写与业务写同事务、失败整体回滚。
     * 由 DynamicTableServiceImpl 收敛点在数据写入后、事件发布前调用。
     *
     * @param eventId 本次写入的幂等键（与随后发布的事件一致）
     */
    public void dispatchSync(String organizationId, String databaseName, String tableCode, String dataId,
                             String eventType, Map<String, Object> before, Map<String, Object> after,
                             String sourceTag, String operatorId, int depth, String eventId) {
        LinkageEvent event = LinkageEvent.of(eventType, tableCode, dataId, before, after);
        event.setOrganizationId(organizationId);
        event.setDatabaseName(databaseName);
        event.setSourceTag(sourceTag);
        event.setOperatorId(operatorId);
        event.setDepth(depth);
        event.setEventId(eventId);
        event.setOccurredAt(new Date());
        List<DynamicLinkageRule> rules = matchRules(event);
        for (DynamicLinkageRule rule : rules) {
            if (!isSyncMode(rule) || depthExceeded(rule, event)) {
                continue;
            }
            executeService.runRule(rule, event, false);
        }
    }

    private List<DynamicLinkageRule> matchRules(LinkageEvent event) {
        if (StringUtils.isEmpty(event.getTableCode()) || StringUtils.isEmpty(event.getEventType())) {
            return List.of();
        }
        try {
            return ruleMapper.selectEnabledByTableAndEvent(event.getTableCode(), event.getEventType());
        } catch (Exception e) {
            log.error("联动规则匹配失败 tableCode={} eventType={}", event.getTableCode(), event.getEventType(), e);
            return List.of();
        }
    }

    private boolean isSyncMode(DynamicLinkageRule rule) {
        return DynamicLinkageRule.MODE_SYNC.equals(rule.getAsyncMode());
    }

    private boolean depthExceeded(DynamicLinkageRule rule, LinkageEvent event) {
        int maxDepth = rule.getMaxDepth() == null ? 1 : rule.getMaxDepth();
        return event.getDepth() >= maxDepth;
    }
}
