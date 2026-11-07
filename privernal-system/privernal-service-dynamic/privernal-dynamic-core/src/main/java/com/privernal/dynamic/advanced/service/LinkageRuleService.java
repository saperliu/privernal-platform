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

package com.privernal.dynamic.advanced.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.dynamic.advanced.domain.DynamicLinkageLog;
import com.privernal.dynamic.advanced.domain.DynamicLinkageRule;
import com.privernal.dynamic.advanced.engine.ActionConfig;
import com.privernal.dynamic.advanced.engine.ConditionEvaluator;
import com.privernal.dynamic.advanced.engine.LinkageContext;
import com.privernal.dynamic.advanced.engine.LinkageEvent;
import com.privernal.dynamic.advanced.engine.LinkageIdemUtils;
import com.privernal.dynamic.advanced.engine.LinkageActionHandler;
import com.privernal.dynamic.advanced.engine.ActionHandlerRegistry;
import com.privernal.dynamic.advanced.engine.LinkageSupport;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageLogMapper;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageRuleMapper;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 联动规则管理（§7.1/§7.5.5/§7.6）：CRUD、保存前校验与配额、启用停用、试跑、执行日志查询。
 * <p>
 * P0 开放四事件（CREATE/UPDATE/DELETE/EFFECTIVE）与三动作（DATA_UPDATE/DATA_CREATE/MSG_INNER）；
 * TIMER/DATE_FIELD 均已接入调度内核（TIMER 走 registerCron；DATE_FIELD 由数据写入钩子按行注册）。
 * 调度任务前缀取消在调度模块落地前为幂等 no-op（§7.7、§8）。
 */
@Slf4j
@Service
public class LinkageRuleService {

    private static final Pattern RULE_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,63}$");
    private static final List<String> P0_EVENTS = List.of(
            DynamicLinkageRule.EVENT_CREATE, DynamicLinkageRule.EVENT_UPDATE,
            DynamicLinkageRule.EVENT_DELETE, DynamicLinkageRule.EVENT_EFFECTIVE,
            DynamicLinkageRule.EVENT_TIMER, DynamicLinkageRule.EVENT_DATE_FIELD);
    private static final List<String> P0_ACTIONS = List.of(
            DynamicLinkageRule.ACTION_DATA_UPDATE, DynamicLinkageRule.ACTION_DATA_CREATE,
            DynamicLinkageRule.ACTION_MSG_INNER, DynamicLinkageRule.ACTION_DATA_DELETE,
            "MSG_SMS");
    private static final int MAX_TIMER_PER_FORM = 5;
    private static final String SCHEDULE_SOURCE = "DESIGNER";
    private static final int MAX_RULES_PER_FORM = 20;
    private static final int MAX_ACTIONS_PER_RULE = 10;
    private static final int MAX_SYNC_PER_FORM_EVENT = 3;

    @Autowired
    private DynamicLinkageRuleMapper ruleMapper;

    @Autowired
    private DynamicLinkageLogMapper logMapper;

    @Autowired
    private ActionHandlerRegistry handlerRegistry;

    @Autowired
    private LinkageSupport linkageSupport;

    @Autowired
    private DynamicTableService dynamicTableService;

    @Autowired
    private com.privernal.job.api.ScheduleTaskClient scheduleTaskClient;

    public List<DynamicLinkageRule> listByFormId(String formId) {
        return ruleMapper.selectList(Wrappers.<DynamicLinkageRule>lambdaQuery()
                .eq(DynamicLinkageRule::getFormId, formId)
                .orderByAsc(DynamicLinkageRule::getCreatedTime));
    }

    public DynamicLinkageRule selectById(String id) {
        return ruleMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public DynamicLinkageRule insertRule(DynamicLinkageRule rule) {
        validate(rule, true);
        applyDefaults(rule);
        fillOrgIdentity(rule);
        ruleMapper.insert(rule);
        if ("Y".equals(rule.getEnabled())) {
            if (DynamicLinkageRule.EVENT_TIMER.equals(rule.getEventType())) {
                registerTimerTask(rule);
            } else if (DynamicLinkageRule.EVENT_DATE_FIELD.equals(rule.getEventType())) {
                // 设计如此（非漏实现）：DATE_FIELD 是"每条数据一个任务"，注册由
                // ① 数据保存钩子 DateExpiryRegistrar（方案甲的窗口内注册）与
                // ② 每日窗口重算 date-field-recalc 两条链路负责，规则保存时不直接扫描存量数据。
                // 代价：新建规则对存量数据最长 24h 后才开始提醒，界面应把这句话讲给配置者。
                log.info("DATE_FIELD 规则已保存：ruleId={}，存量数据的到期任务将由窗口重算补齐", rule.getId());
            }
        }
        return rule;
    }

    @Transactional(rollbackFor = Exception.class)
    public DynamicLinkageRule updateRule(DynamicLinkageRule rule) {
        DynamicLinkageRule existing = ruleMapper.selectById(rule.getId());
        if (existing == null) {
            throw new ServiceException("联动规则不存在：" + rule.getId());
        }
        if (!Objects.equals(existing.getRuleCode(), rule.getRuleCode())) {
            throw new ServiceException("rule_code 保存后不可修改（它是调度 biz_key 与应用包判重键，§7.6）");
        }
        if (!Objects.equals(existing.getEventType(), rule.getEventType())) {
            // 事件类型变更对 TIMER 等价于换任务，P0 事件间切换也要求走"删+建"，避免幽灵触发（§7.7.4）
            throw new ServiceException("事件类型不可直接修改，请新建规则");
        }
        rule.setFormId(existing.getFormId());
        rule.setOrganizationId(existing.getOrganizationId());
        validate(rule, false);
        applyDefaults(rule);
        ruleMapper.updateById(rule);
        if (DynamicLinkageRule.EVENT_TIMER.equals(rule.getEventType())) {
            if ("Y".equals(rule.getEnabled())) {
                // 幂等 upsert：cron 变更即覆盖改期（§7.7.1/§7.7.4）
                registerTimerTask(rule);
            } else {
                cancelTimerTask(rule);
            }
        } else if (DynamicLinkageRule.EVENT_DATE_FIELD.equals(rule.getEventType())) {
            // P0-4（此前两个分支皆空）：offsetDays/windowDays 一变，旧的 per-row 任务就会按
            // 提前量错位的时刻到点提醒，因此无论启用还是停用都先按前缀取消；
            // 启用时由每日窗口重算（date-field-recalc）重新登记窗口内的数据，规则本身不直接注册。
            cancelDateFieldTasks(rule);
            if ("Y".equals(rule.getEnabled())) {
                log.info("DATE_FIELD 规则已更新，旧到期任务已取消，待窗口重算重新登记：ruleId={}", rule.getId());
            } else {
                log.info("DATE_FIELD 规则已停用，其全部到期提醒任务已取消：ruleId={}", rule.getId());
            }
        }
        return rule;
    }

    /** department_id/organization_id 无自动填充（动态表业务代码各自显式落），这里统一补齐会话归属 */
    private void fillOrgIdentity(DynamicLinkageRule rule) {
        if (com.privernal.common.core.utils.StringUtils.isEmpty(rule.getOrganizationId())) {
            rule.setOrganizationId(com.privernal.common.security.service.SecurityUtils.getOrganizationId());
        }
        if (com.privernal.common.core.utils.StringUtils.isEmpty(rule.getDepartmentId())) {
            rule.setDepartmentId(com.privernal.common.security.service.SecurityUtils.getDepartmentId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, String enabled) {
        DynamicLinkageRule existing = ruleMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException("联动规则不存在：" + id);
        }
        DynamicLinkageRule update = new DynamicLinkageRule();
        update.setId(id);
        update.setEnabled("Y".equals(enabled) ? "Y" : "N");
        ruleMapper.updateById(update);
        if ("N".equals(update.getEnabled())) {
            // 规则停用取消已注册调度任务（§7.7.4）：TIMER 精确取消、DATE_FIELD 前缀取消全部 per-row 任务
            cancelTimerTask(existing);
            cancelDateFieldTasks(existing);
        } else if (DynamicLinkageRule.EVENT_TIMER.equals(existing.getEventType())) {
            registerTimerTask(ruleMapper.selectById(id));
        } else if (DynamicLinkageRule.EVENT_DATE_FIELD.equals(existing.getEventType())) {
            // 重新启用：前缀取消 + 窗口重算（由 DateFieldRecalcActionHandler 的重算链路兜底）
            // 方案甲下注册即由数据钩子与每日重算覆盖，这里只需保证规则状态正确
        }
    }

    /** P0-4：DATE_FIELD 规则停用/删除时按前缀取消其全部 per-row 任务（此前为空分支，任务悬挂继续发提醒） */
    private void cancelDateFieldTasks(DynamicLinkageRule rule) {
        com.privernal.dynamic.dbs.domain.DynamicForm formObj = linkageSupport.loadForm(rule.getFormId());
        if (formObj == null || StringUtils.isEmpty(formObj.getTableCode())) {
            return;
        }
        scheduleTaskClient.cancelByPrefix(rule.getOrganizationId(), LinkageBizKeys.SOURCE_DATE_FIELD,
                LinkageBizKeys.dateExpiryByRule(formObj.getTableCode(), rule.getRuleCode()));
    }


    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(String id) {
        DynamicLinkageRule existing = ruleMapper.selectById(id);
        if (existing == null) {
            return;
        }
        // uk_form_code(form_id, rule_code) 会被软删行占位（§7.1 注）：必须先改码再逻辑删——
        // 逻辑删后的行 updateById 会被逻辑删插件追加 deleted_flag='N' 过滤，改码会静默失败。
        // 与表单删除改 table_code 的 `_deleted_{id}` 同一口径
        DynamicLinkageRule rename = new DynamicLinkageRule();
        rename.setId(id);
        String renamed = existing.getRuleCode() + "_deleted_" + id;
        rename.setRuleCode(renamed.length() > 64 ? renamed.substring(0, 64) : renamed);
        ruleMapper.updateById(rename);
        ruleMapper.deleteById(id);
        // 规则删除取消已注册任务（§7.7.4）：TIMER 精确取消，DATE_FIELD 按 ruleCode 前缀取消其全部 per-row 任务
        if (DynamicLinkageRule.EVENT_TIMER.equals(existing.getEventType())) {
            cancelTimerTask(existing);
        } else if (DynamicLinkageRule.EVENT_DATE_FIELD.equals(existing.getEventType())) {
            // DATE_FIELD 规则删除必须前缀取消其全部 per-row 任务（P0-4：此前为空分支，任务悬挂继续发提醒）
            cancelDateFieldTasks(existing);
        } else {
            log.info("规则类型 {} 无需取消调度任务 rule={}", existing.getEventType(), existing.getRuleCode());
        }
    }

    /**
     * DATE_FIELD 规则校验（high-setting §7.7.3，P2-B1 打开）：
     * event_config = {fieldCode, offsetDays?, windowDays?}；targetMode 强制 PER_ROW
     * （到期语义是"对注册快照的那一行触发"，整表模式无意义）。per-row 任务的注册
     * 不在保存时发生——由数据写入钩子 DateExpiryRegistrar 按行注册（§11 配额闸方案甲：
     * 只注册 windowDays 窗口内到期，防止 10 万行表单击穿组织配额）。
     */
    private void validateDateField(DynamicLinkageRule rule) {
        String config = rule.getEventConfig();
        if (StringUtils.isEmpty(config)) {
            throw new ServiceException("DATE_FIELD 规则必须配置 eventConfig（fieldCode/offsetDays）");
        }
        com.alibaba.fastjson2.JSONObject cfg;
        try {
            cfg = com.alibaba.fastjson2.JSON.parseObject(config);
        } catch (Exception e) {
            throw new ServiceException("eventConfig 不是合法 JSON");
        }
        String fieldCode = cfg.getString("fieldCode");
        if (StringUtils.isEmpty(fieldCode)) {
            throw new ServiceException("DATE_FIELD 规则缺少 fieldCode（到期日期字段编码）");
        }
        Integer offsetDays = cfg.getInteger("offsetDays");
        if (offsetDays != null && (offsetDays < -3650 || offsetDays > 3650)) {
            throw new ServiceException("offsetDays 取值范围 -3650 ~ 3650");
        }
        Integer windowDays = cfg.getInteger("windowDays");
        if (windowDays != null && (windowDays < 1 || windowDays > 365)) {
            throw new ServiceException("windowDays（注册窗口）取值范围 1 ~ 365");
        }
        if (!"PER_ROW".equals(rule.getTargetMode()) && StringUtils.isNotEmpty(rule.getTargetMode())) {
            throw new ServiceException("DATE_FIELD 规则 targetMode 只能为 PER_ROW");
        }
    }

    /** 保存前校验（§7.6）：编码/事件/动作/配额/SYNC 限制 */
    private void validate(DynamicLinkageRule rule, boolean insert) {
        if (StringUtils.isEmpty(rule.getName())) {
            throw new ServiceException("规则名称不能为空");
        }
        if (!RULE_CODE_PATTERN.matcher(StringUtils.defaultString(rule.getRuleCode())).matches()) {
            throw new ServiceException("规则编码须匹配 [a-z][a-z0-9_]{2,63}");
        }
        if (!P0_EVENTS.contains(rule.getEventType())) {
            throw new ServiceException("事件类型 " + rule.getEventType() + " 不支持");
        }
        if (DynamicLinkageRule.EVENT_TIMER.equals(rule.getEventType())) {
            validateTimerCron(rule);
        }
        if (DynamicLinkageRule.EVENT_DATE_FIELD.equals(rule.getEventType())) {
            validateDateField(rule);
        }
        if (rule.getMaxDepth() != null && (rule.getMaxDepth() < 1 || rule.getMaxDepth() > 3)) {
            throw new ServiceException("级联深度允许 1~3");
        }
        List<DynamicLinkageRule> existingRules = listByFormId(rule.getFormId());
        if (DynamicLinkageRule.EVENT_TIMER.equals(rule.getEventType()) && "Y".equals(rule.getEnabled())) {
            long enabledTimerCount = existingRules.stream()
                    .filter(r -> DynamicLinkageRule.EVENT_TIMER.equals(r.getEventType())
                            && "Y".equals(r.getEnabled())
                            && !r.getId().equals(rule.getId()))
                    .count();
            if (enabledTimerCount >= MAX_TIMER_PER_FORM) {
                throw new ServiceException("每表单 TIMER 规则最多 " + MAX_TIMER_PER_FORM + " 条（§7.5.5）");
            }
        }
        if (insert) {
            if (existingRules.size() >= MAX_RULES_PER_FORM) {
                throw new ServiceException("每表单联动规则最多 " + MAX_RULES_PER_FORM + " 条（§7.5.5 配额）");
            }
            boolean codeDuplicated = existingRules.stream()
                    .anyMatch(r -> Objects.equals(r.getRuleCode(), rule.getRuleCode()));
            if (codeDuplicated) {
                throw new ServiceException("规则编码在表单内已存在：" + rule.getRuleCode());
            }
        }
        if (DynamicLinkageRule.EVENT_TIMER.equals(rule.getEventType())
                && DynamicLinkageRule.MODE_SYNC.equals(rule.getAsyncMode())) {
            // TIMER 没有触发行上下文，SYNC 同事务无从依附（§7.5.2）
            throw new ServiceException("TIMER 规则只能为 ASYNC");
        }
        if (DynamicLinkageRule.MODE_SYNC.equals(rule.getAsyncMode())) {
            long syncCount = existingRules.stream()
                    .filter(r -> DynamicLinkageRule.MODE_SYNC.equals(r.getAsyncMode()))
                    .filter(r -> Objects.equals(r.getEventType(), rule.getEventType()))
                    .filter(r -> !r.getId().equals(rule.getId()))
                    .count();
            if (syncCount >= MAX_SYNC_PER_FORM_EVENT) {
                log.info("同表单同事件 SYNC 规则已满 {}，本条强制转 ASYNC（§7.5.5）", MAX_SYNC_PER_FORM_EVENT);
                rule.setAsyncMode(DynamicLinkageRule.MODE_ASYNC);
            } else {
                assertSyncOnlyDataActions(rule);
            }
        }
        validateActions(rule);
    }

    private void assertSyncOnlyDataActions(DynamicLinkageRule rule) {
        for (ActionConfig action : parseActions(rule.getActions())) {
            if (!DynamicLinkageRule.ACTION_DATA_UPDATE.equals(action.getType())
                    && !DynamicLinkageRule.ACTION_DATA_CREATE.equals(action.getType())) {
                throw new ServiceException("SYNC 规则只能配数据动作（DATA_UPDATE/DATA_CREATE），消息类请用 ASYNC（§7.5.2）");
            }
        }
    }

    private void validateActions(DynamicLinkageRule rule) {
        List<ActionConfig> actions = parseActions(rule.getActions());
        if (actions.isEmpty()) {
            throw new ServiceException("至少配置一个动作");
        }
        if (actions.size() > MAX_ACTIONS_PER_RULE) {
            throw new ServiceException("每规则动作最多 " + MAX_ACTIONS_PER_RULE + " 个（§7.5.5 配额）");
        }
        for (ActionConfig action : actions) {
            if (!P0_ACTIONS.contains(action.getType())) {
                throw new ServiceException("动作类型 " + action.getType() + " 暂未开放（P0 支持："
                        + String.join("/", P0_ACTIONS) + "）");
            }
            if (handlerRegistry.get(action.getType()) == null) {
                throw new ServiceException("动作类型 " + action.getType() + " 没有注册处理器");
            }
            if (DynamicLinkageRule.ACTION_DATA_UPDATE.equals(action.getType())) {
                JSONObject config = action.getConfig();
                if (config == null || StringUtils.isEmpty(config.getString("targetFormId"))) {
                    throw new ServiceException("DATA_UPDATE 动作必须选择目标表单");
                }
                if (config.getJSONArray("set") == null || config.getJSONArray("set").isEmpty()) {
                    throw new ServiceException("DATA_UPDATE 动作必须配置至少一个 SET 字段");
                }
            }
            if (DynamicLinkageRule.ACTION_DATA_CREATE.equals(action.getType())) {
                JSONObject config = action.getConfig();
                if (config == null || StringUtils.isEmpty(config.getString("targetFormId"))) {
                    throw new ServiceException("DATA_CREATE 动作必须选择目标表单");
                }
            }
            if (DynamicLinkageRule.ACTION_MSG_INNER.equals(action.getType())) {
                JSONObject config = action.getConfig();
                if (config == null || StringUtils.isEmpty(config.getString("title"))) {
                    throw new ServiceException("MSG_INNER 动作必须配置标题");
                }
            }
            if (DynamicLinkageRule.ACTION_DATA_DELETE.equals(action.getType())) {
                JSONObject config = action.getConfig();
                if (config == null || StringUtils.isEmpty(config.getString("targetFormId"))) {
                    throw new ServiceException("DATA_DELETE 动作必须选择目标表单");
                }
                // 删除动作强制有条件（§7.4：规则里禁配无条件的删除），配置页强制预览
                JSONObject deleteCondition = config.getJSONObject("conditionTree");
                if (deleteCondition == null || deleteCondition.isEmpty()) {
                    throw new ServiceException("DATA_DELETE 动作必须配置删除条件（禁配无条件的删除，§7.4）");
                }
            }
        }
        validateConditionTree(rule.getConditionTree());
    }

    private void validateConditionTree(String conditionTree) {
        if (StringUtils.isEmpty(conditionTree)) {
            return;
        }
        try {
            JSONObject tree = JSON.parseObject(conditionTree);
            assertConditionDepth(tree, 0);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("条件树不是合法 JSON：" + e.getMessage());
        }
    }

    private void assertConditionDepth(JSONObject node, int depth) {
        if (node == null) {
            return;
        }
        if (depth > ConditionEvaluator.MAX_DEPTH) {
            throw new ServiceException("条件树超过最大深度 " + ConditionEvaluator.MAX_DEPTH);
        }
        var children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                assertConditionDepth(children.getJSONObject(i), depth + 1);
            }
        }
    }

    private void applyDefaults(DynamicLinkageRule rule) {
        if (rule.getEnabled() == null) {
            rule.setEnabled("Y");
        }
        if (rule.getAsyncMode() == null) {
            rule.setAsyncMode(DynamicLinkageRule.MODE_ASYNC);
        }
        if (rule.getFailPolicy() == null) {
            rule.setFailPolicy("CONTINUE");
        }
        if (rule.getMaxDepth() == null) {
            rule.setMaxDepth(1);
        }
        if (rule.getMaxRowsPerFire() == null) {
            rule.setMaxRowsPerFire(5000);
        }
        if (rule.getTimeoutSeconds() == null) {
            rule.setTimeoutSeconds(300);
        }
        if (DynamicLinkageRule.EVENT_TIMER.equals(rule.getEventType())
                && StringUtils.isEmpty(rule.getTargetMode())) {
            rule.setTargetMode("PER_ROW");
        }
    }

    /** TIMER cron 校验（§7.6/§7.7.2）：必须为 6 位秒级且 Spring CronExpression 可解析，不支持 7 位/年与时区入参 */
    private void validateTimerCron(DynamicLinkageRule rule) {
        JSONObject eventConfig = parseJson(rule.getEventConfig());
        String cron = eventConfig == null ? null : eventConfig.getString("cron");
        if (StringUtils.isEmpty(cron)) {
            throw new ServiceException("TIMER 规则必须在事件配置里给出 cron");
        }
        if (cron.trim().split("\\s+").length != 6) {
            throw new ServiceException("TIMER cron 必须为 6 位秒级表达式（分 时 日 月 周 前还有秒位），不支持 7 位/年");
        }
        try {
            org.springframework.scheduling.support.CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("TIMER cron 无法解析：" + e.getMessage());
        }
    }

    /** 注册/改期 TIMER 调度任务（bizKey=link:{tableCode}:{ruleCode}，§7.7.1；幂等 upsert） */
    private void registerTimerTask(DynamicLinkageRule rule) {
        if (rule == null) {
            return;
        }
        try {
            com.privernal.dynamic.dbs.domain.DynamicForm form = linkageSupport.loadForm(rule.getFormId());
            if (form == null || StringUtils.isEmpty(form.getTableCode())) {
                log.warn("TIMER 规则关联表单缺失，未注册调度任务 rule={}", rule.getRuleCode());
                return;
            }
            String orgId = rule.getOrganizationId();
            String bizKey = LinkageBizKeys.timer(form.getTableCode(), rule.getRuleCode());
            String cron = parseJson(rule.getEventConfig()) == null ? null
                    : parseJson(rule.getEventConfig()).getString("cron");
            if (StringUtils.isEmpty(cron)) {
                log.warn("TIMER 规则缺少 cron，未注册调度任务 rule={}", rule.getRuleCode());
                return;
            }
            String database = linkageSupport.resolveDatabase(orgId);
            JSONObject config = new JSONObject();
            config.put("ruleId", rule.getId());
            config.put("ruleCode", rule.getRuleCode());
            config.put("tableCode", form.getTableCode());
            config.put("formId", form.getId());
            scheduleTaskClient.registerCron(orgId, SCHEDULE_SOURCE, bizKey, rule.getRuleCode(),
                    cron.trim(), "LINKAGE_TIMER", config, database);
            log.info("TIMER 规则已注册调度任务 rule={} bizKey={} cron={}", rule.getRuleCode(), bizKey, cron);
        } catch (Exception e) {
            log.error("TIMER 调度任务注册失败 rule={}", rule.getRuleCode(), e);
            throw new ServiceException("TIMER 调度任务注册失败：" + e.getMessage());
        }
    }

    /** 取消 TIMER 调度任务（禁用/删除，§7.7.4） */
    private void cancelTimerTask(DynamicLinkageRule rule) {
        if (rule == null) {
            return;
        }
        try {
            com.privernal.dynamic.dbs.domain.DynamicForm form = linkageSupport.loadForm(rule.getFormId());
            if (form == null || StringUtils.isEmpty(form.getTableCode())) {
                return;
            }
            scheduleTaskClient.cancel(rule.getOrganizationId(), SCHEDULE_SOURCE,
                    LinkageBizKeys.timer(form.getTableCode(), rule.getRuleCode()));
        } catch (Exception e) {
            log.error("TIMER 调度任务取消失败 rule={}", rule.getRuleCode(), e);
        }
    }

    private List<ActionConfig> parseActions(String actions) {
        try {
            List<ActionConfig> list = JSON.parseArray(actions, ActionConfig.class);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            throw new ServiceException("动作配置不是合法 JSON");
        }
    }

    /**
     * 试跑（§7.5.5）：选一条历史数据模拟事件，返回条件判定与逐动作 dry-run（不真正执行）。
     * 数据更新/删除动作的 dry-run 返回实际命中主键列表（与"删除前预览"同一条查询）。
     */
    public Map<String, Object> dryRun(String ruleId, String dataId) {
        DynamicLinkageRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new ServiceException("联动规则不存在：" + ruleId);
        }
        DynamicForm form = linkageSupport.loadForm(rule.getFormId());
        if (form == null) {
            throw new ServiceException("规则关联表单不存在");
        }
        String database = linkageSupport.resolveDatabase(form.getOrganizationId());
        Map<String, Object> row;
        String oldOrg = OrganizationContextHolder.getOrganizationId();
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
            OrganizationContextHolder.setOrganizationId(form.getOrganizationId());
            row = dynamicTableService.findById(database, form.getTableCode(), dataId);
        } finally {
            if (oldOrg == null) {
                OrganizationContextHolder.clear();
            } else {
                OrganizationContextHolder.setOrganizationId(oldOrg);
                OrganizationContextHolder.setIgnore(Boolean.TRUE.equals(oldIgnore));
            }
        }
        if (row == null) {
            throw new ServiceException("试跑数据不存在：" + dataId);
        }
        LinkageEvent event = LinkageEvent.of(rule.getEventType(), form.getTableCode(), dataId, null, row);
        event.setOrganizationId(form.getOrganizationId());
        event.setDatabaseName(database);
        event.setSourceTag("NORMAL");
        LinkageContext ctx = new LinkageContext(rule, event, 1);

        Map<String, Object> result = new HashMap<>();
        ConditionEvaluator.ConditionResult condition =
                ConditionEvaluator.evaluate(parseJson(rule.getConditionTree()), row);
        result.put("conditionPass", condition.pass());
        result.put("conditionTrace", condition.trace());
        var previews = new JSONArray();
        for (ActionConfig action : parseActions(rule.getActions())) {
            JSONObject item = new JSONObject();
            item.put("type", action.getType());
            LinkageActionHandler handler = handlerRegistry.get(action.getType());
            if (handler == null) {
                item.put("error", "动作处理器未注册");
            } else {
                try {
                    item.put("preview", handler.dryRun(action, ctx));
                } catch (Exception e) {
                    item.put("error", e.getMessage());
                }
            }
            previews.add(item);
        }
        result.put("actions", previews);
        return result;
    }

    private JSONObject parseJson(String text) {
        return StringUtils.isEmpty(text) ? null : JSON.parseObject(text);
    }

    /** 执行日志分页查询（联动日志页，§7.6） */
    public Map<String, Object> selectLogPage(Map<String, Object> filter) {
        long page = filter.get("pageNum") == null ? 1 : Long.parseLong(String.valueOf(filter.get("pageNum")));
        long size = filter.get("pageSize") == null ? 20 : Long.parseLong(String.valueOf(filter.get("pageSize")));
        size = Math.min(Math.max(size, 1), 200);
        Map<String, Object> condition = new HashMap<>(filter);
        condition.put("offset", (page - 1) * size);
        condition.put("limit", size);
        Map<String, Object> result = new HashMap<>();
        result.put("total", logMapper.countLogPage(condition));
        result.put("rows", logMapper.selectLogPage(condition));
        return result;
    }
}
