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

package com.privernal.dynamic.advanced.trigger;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.dynamic.advanced.domain.DynamicLinkageRule;
import com.privernal.dynamic.advanced.engine.LinkageSupport;
import com.privernal.dynamic.advanced.engine.SafeConditionSql;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageQueryMapper;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageRuleMapper;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.job.api.ScheduleTaskClient;
import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleAction;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统动作：DATE_FIELD 每日全量差量重算（配额闸方案甲的另一半，P2-B）。
 *
 * <p>写入钩子（DateExpiryRegistrar）覆盖正常与导入链路，但两条路径会漏：
 * ① 数据永远不再更新的存量行滑入窗口；② 注册时窗口外、此后无更新的行。
 * SYSTEM 任务 date-field-recalc（每日一次，建议凌晨）对每条启用的 DATE_FIELD 规则
 * 全量扫描表内行，差量对齐任务行：窗口内应注册的补注册（registerOnce 幂等改期/复活）、
 * 窗口外或字段已清空的取消（cancel）。</p>
 *
 * <p>行为与写入钩子完全同口径：历史数据（到期早于昨日）不注册；临期 30s 补提醒；
 * 单规则单批 LIMIT 50000 截断告警。注册/取消走独立线程池批量执行。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduleAction("date-field-recalc")
public class DateFieldRecalcActionHandler implements ScheduleActionHandler {

    private static final int SCAN_LIMIT = 50000;

    private final DynamicLinkageRuleMapper ruleMapper;

    private final DynamicLinkageQueryMapper linkageQueryMapper;

    private final LinkageSupport linkageSupport;

    /**
     * 字段注入 + @Lazy（不用构造注入）：ScheduleTaskService 构造链经 registry 的
 * getBeansOfType 会把"创建中"的 handler 静默跳过（关键缺陷第 1 条，
     * WorkflowTimeoutActionHandler 同坑），注入点必须打断构造环。
     */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private ScheduleTaskClient scheduleTaskClient;

    private final java.util.concurrent.ExecutorService recalcExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
                Thread thread = new Thread(r, "date-field-recalc");
                thread.setDaemon(true);
                return thread;
            });

    @Override
    public String actionType() {
        return "BEAN";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        List<DynamicLinkageRule> rules = ruleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DynamicLinkageRule>()
                        .eq(DynamicLinkageRule::getEventType, DynamicLinkageRule.EVENT_DATE_FIELD)
                        .eq(DynamicLinkageRule::getEnabled, "Y"));
        if (rules.isEmpty()) {
            return ActionResult.ok("no enabled DATE_FIELD rules");
        }
        List<java.util.concurrent.Future<int[]>> futures = new ArrayList<>();
        for (DynamicLinkageRule rule : rules) {
            futures.add(recalcExecutor.submit(() -> recalcRule(rule)));
        }
        int registered = 0;
        int cancelled = 0;
        int failed = 0;
        for (java.util.concurrent.Future<int[]> future : futures) {
            try {
                int[] counts = future.get();
                registered += counts[0];
                cancelled += counts[1];
                failed += counts[2];
            } catch (Exception e) {
                log.error("[date-recalc] rule recalc failed", e);
                failed++;
            }
        }
        return ActionResult.ok("rules=" + rules.size() + " registered=" + registered
                + " cancelled=" + cancelled + " failed=" + failed);
    }

    /** 单规则差量重算，返回 {registered, cancelled, failed} */
    private int[] recalcRule(DynamicLinkageRule rule) {
        int[] counts = new int[3];
        try {
            DynamicForm form = linkageSupport.loadForm(rule.getFormId());
            if (form == null || StringUtils.isEmpty(form.getTableCode())) {
                return counts;
            }
            String tableName = form.getTableCode();
            JSONObject cfg = parse(rule.getEventConfig());
            if (cfg == null) {
                return counts;
            }
            String fieldCode = cfg.getString("fieldCode");
            int offsetDays = cfg.getIntValue("offsetDays", 0);
            int windowDays = cfg.getIntValue("windowDays", 30);
            if (StringUtils.isEmpty(fieldCode)) {
                return counts;
            }
            String snake = fieldCode.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
            if (!SafeConditionSql.isValidIdentifier(tableName)) {
                return counts;
            }
            LinkageSupport.assertLinkageIgnoreScope();
            String sql = "SELECT `id`, `" + snake + "` FROM `" + tableName
                    + "` WHERE deleted_flag = 'N' ORDER BY `id` ASC LIMIT " + SCAN_LIMIT;
            List<Map<String, Object>> rows = linkageQueryMapper.selectRows(sql, new HashMap<>());
            if (rows.size() >= SCAN_LIMIT) {
                log.warn("[date-recalc] 规则 {} 行数达到扫描上限 {}，可能截断", rule.getRuleCode(), SCAN_LIMIT);
            }
            Date now = new Date();
            Date windowEnd = addDays(now, windowDays);
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                if (id == null) {
                    continue;
                }
                String dataId = String.valueOf(id);
                // P0-2：与保存钩子 DateExpiryRegistrar 共用同一个 key 构造入口，
                // 否则这里的"窗口外取消"永远打不中那边注册的行（任务只增不减）
                String bizKey = com.privernal.dynamic.advanced.service.LinkageBizKeys
                        .dateExpiry(tableName, rule.getRuleCode(), dataId);
                Date due = parseDate(row.get(snake));
                try {
                    if (due == null || due.before(addDays(now, -1)) || due.after(windowEnd)) {
                        // 窗口外/字段空/历史：取消（幂等，无任务行时 no-op）
                        if (scheduleTaskClient.cancel(rule.getOrganizationId(), "DESIGNER_DATA", bizKey)) {
                            counts[1]++;
                        }
                        continue;
                    }
                    Date fireTime = due.before(now) ? new Date(now.getTime() + 30_000L) : due;
                    JSONObject actionConfig = new JSONObject();
                    actionConfig.put("ruleId", rule.getId());
                    JSONObject bizContext = new JSONObject();
                    bizContext.put("dataId", dataId);
                    bizContext.put("due", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(due));
                    scheduleTaskClient.registerOnce(rule.getOrganizationId(), "DESIGNER_DATA", bizKey, fireTime,
                            "LINKAGE_DATE_FIRE", actionConfig.toString(), bizContext.toString(), null);
                    counts[0]++;
                } catch (Exception e) {
                    counts[2]++;
                    log.warn("[date-recalc] 行处理失败 rule={} dataId={}", rule.getRuleCode(), dataId, e);
                }
            }
            log.info("[date-recalc] rule={} table={} rows={} registered={} cancelled={} failed={}",
                    rule.getRuleCode(), tableName, rows.size(), counts[0], counts[1], counts[2]);
        } catch (Exception e) {
            log.error("[date-recalc] 规则级失败 rule={}", rule.getRuleCode(), e);
            counts[2]++;
        }
        return counts;
    }

    private JSONObject parse(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return null;
        }
    }

    private Date parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date d) {
            return d;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return new Date(ts.getTime());
        }
        if (value instanceof java.time.LocalDateTime local) {
            return Date.from(local.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        if (value instanceof java.time.LocalDate localDate) {
            return java.sql.Date.valueOf(localDate);
        }
        if (value instanceof Number num) {
            return new Date(num.longValue());
        }
        String str = String.valueOf(value).trim();
        for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"}) {
            try {
                return new SimpleDateFormat(pattern).parse(str);
            } catch (Exception ignored) {
                // 尝试下一个格式
            }
        }
        return null;
    }

    private Date addDays(Date value, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(value);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }
}
