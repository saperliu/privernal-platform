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
import com.privernal.dynamic.advanced.mapper.DynamicLinkageRuleMapper;
import com.privernal.dynamic.advanced.service.LinkageBizKeys;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.job.api.ScheduleTaskClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DATE_FIELD 数据到期注册器（DESIGNER_DATA / high-setting ，P2-B1）：
 * 数据写入（新增/更新）后按启用的 DATE_FIELD 规则注册/改期一次性任务——
 * fireTime = 该行 fieldCode 值 − offsetDays（正数=提前 N 天提醒），biz_key = linkdate:{tableCode}:{ruleCode}:{dataId}，
 * 到点经 LINKAGE_DATE_FIRE 动作回调联动分发器（删除级联取消走 {@code cancelForDeletedRow} 按规则精确取消，P0-5/P2-2）。
 *
 * <p>配额闸（§11 方案甲）：只注册 windowDays（event_config.windowDays，默认 30）天内到期的数据，
 * 窗口外跳过并记日志——防止"10 万行表单 × 1 条规则"击穿组织 20,000 配额；历史数据（到期早于
 * 昨日）不注册；临期（到期早于今日但晚于昨日）注册为 30s 后补提醒。写入时注册覆盖正常与导入链路，
 * 每日全量差量重算留待二期（写入路径遗漏时由该机制兜底，见 .B）。</p>
 *
 * <p>线程与事务：调用点处于业务库事务且 @DS 业务库上下文，注册动作仿 WebhookEventService——
 * afterCommit 后换独立线程执行（回滚不注册，且不占业务库连接）。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DateExpiryRegistrar {

    /** 规则启用状态缓存：tableCode → (rules, expireAt)，60s TTL，规则量小避免每次写入查主库 */
    private final Map<String, CacheEntry> ruleCache = new ConcurrentHashMap<>();

    private static final long CACHE_TTL_MS = 60_000L;

    private final DynamicLinkageRuleMapper ruleMapper;

    private final LinkageSupport linkageSupport;

    private final ScheduleTaskClient scheduleTaskClient;

    @org.springframework.beans.factory.annotation.Autowired
    private com.privernal.dynamic.advanced.mapper.DynamicLinkageQueryMapper linkageQueryMapper;

    private final ExecutorService registerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "date-expiry-register");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 数据写入后的注册入口（saveOrUpdate 收敛点调用）。参数在调用线程捕获，
     * 实际注册延迟到事务提交后异步执行。
     */
    public void onDataWrite(String database, String tableName, Map<String, Object> data, String dataId) {
        if (data == null || StringUtils.isEmpty(dataId) || StringUtils.isEmpty(tableName)) {
            return;
        }
        List<DynamicLinkageRule> rules = rulesForTable(tableName);
        if (rules.isEmpty()) {
            return;
        }
        // 调用线程尚在事务/业务库上下文：先捕获注册所需参数。
        // orgId 双路：TTL（异步链路显式 set）优先，回落会话（Web 请求路径的 TTL 未必有人写）
        String orgIdResolved = OrganizationContextHolder.getOrganizationId();
        if (StringUtils.isEmpty(orgIdResolved)) {
            orgIdResolved = com.privernal.common.security.service.SecurityUtils.getOrganizationId();
        }
        final String organizationId = orgIdResolved;
        Date captureTime = new Date();
        if (StringUtils.isEmpty(organizationId)) {
            log.warn("DATE_FIELD 注册缺少组织上下文，跳过 table={} dataId={}", tableName, dataId);
            return;
        }
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            submitRegister(rules, database, tableName, data, dataId, organizationId, captureTime);
                        }
                    });
        } else {
            submitRegister(rules, database, tableName, data, dataId, organizationId, captureTime);
        }
    }

    private void submitRegister(List<DynamicLinkageRule> rules, String database, String tableName,
                                Map<String, Object> data, String dataId, String organizationId, Date captureTime) {
        registerExecutor.submit(() -> {
            for (DynamicLinkageRule rule : rules) {
                try {
                    registerForRule(rule, database, tableName, data, dataId, organizationId, captureTime);
                } catch (Exception e) {
                    log.error("DATE_FIELD 注册失败 rule={} dataId={}", rule.getRuleCode(), dataId, e);
                }
            }
        });
    }

    private void registerForRule(DynamicLinkageRule rule, String database, String tableName,
                                 Map<String, Object> data, String dataId, String organizationId, Date captureTime) {
        JSONObject cfg;
        try {
            cfg = JSON.parseObject(rule.getEventConfig());
        } catch (Exception e) {
            log.warn("DATE_FIELD 规则 eventConfig 解析失败 rule={}", rule.getRuleCode());
            return;
        }
        String fieldCode = cfg.getString("fieldCode");
        int offsetDays = cfg.getIntValue("offsetDays", 0);
        int windowDays = cfg.getIntValue("windowDays", 30);
        if (StringUtils.isEmpty(fieldCode)) {
            return;
        }
        Date fieldValue = parseDate(readFieldValue(data, tableName, dataId, database, fieldCode));
        if (fieldValue == null) {
            // 该行未填到期字段：注册/改期语义下应取消既有任务（改清空日期 = 不再提醒）
            scheduleTaskClient.cancel(organizationId, "DESIGNER_DATA",
                    bizKey(tableName, rule.getRuleCode(), dataId));
            return;
        }
        // offsetDays 语义（设计原文 §7.7.3）：fireTime = 字段值 − offsetDays，正数=提前 N 天提醒
        Date due = addDays(fieldValue, -offsetDays);
        Date now = captureTime;
        if (due.before(new Date(now.getTime() - 86_400_000L))) {
            // 历史数据：到期早于昨日，不再提醒
            return;
        }
        Date fireTime;
        if (due.before(now)) {
            // 临期（昨日~现在之间到期）：30s 后补提醒
            fireTime = new Date(now.getTime() + 30_000L);
        } else if (due.after(new Date(now.getTime() + windowDays * 86_400_000L))) {
            // §11 配额闸：超出注册窗口，跳过（窗口滑入后由数据更新触发重新注册）
            log.info("DATE_FIELD 到期超出注册窗口 {} 天，跳过注册 rule={} dataId={} due={}",
                    windowDays, rule.getRuleCode(), dataId, due);
            return;
        } else {
            fireTime = due;
        }
        JSONObject actionConfig = new JSONObject();
        actionConfig.put("ruleId", rule.getId());
        JSONObject bizContext = new JSONObject();
        bizContext.put("dataId", dataId);
        bizContext.put("due", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(due));
        scheduleTaskClient.registerOnce(organizationId, "DESIGNER_DATA",
                bizKey(tableName, rule.getRuleCode(), dataId), fireTime,
                "LINKAGE_DATE_FIRE", actionConfig.toString(), bizContext.toString(), database);
        log.info("[date-expiry] registered rule={} table={} dataId={} fire={}",
                rule.getRuleCode(), tableName, dataId, fireTime);
    }

    /**
     * 到期字段取值：写入 data（驼峰键）优先；DB 默认列（created_time 等）不在内存 data 里，
     * 回查已提交的行兜底（下划线列名）。查不到返回 null。
     */
    private Object readFieldValue(Map<String, Object> data, String tableName, String dataId,
                                  String database, String fieldCode) {
        Object value = data.get(fieldCode);
        if (value != null) {
            return value;
        }
        String snake = fieldCode.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        value = data.get(snake);
        if (value != null) {
            return value;
        }
        try {
            if (!com.privernal.dynamic.advanced.engine.SafeConditionSql.isValidIdentifier(tableName)
                    || !dataId.matches("[a-zA-Z0-9_-]{1,64}")) {
                log.warn("DATE_FIELD 回查参数非法 table={} dataId={}", tableName, dataId);
                return null;
            }
            // 走联动引擎的 selectRows 直查（findById 的 @DS 路径在独立线程上有 pstmt 参数槽异常）；
            // dataId 先白名单校验，tableName 来自表单物理表名
            LinkageSupport.assertLinkageIgnoreScope();
            String sql = "SELECT * FROM `" + tableName + "` WHERE id = #{params.p0} LIMIT 1";
            List<Map<String, Object>> rows = linkageQueryMapper.selectRows(sql, java.util.Map.of("p0", dataId));
            if (rows != null && !rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                value = row.get(snake);
                if (value == null) {
                    value = row.get(fieldCode);
                }
            }
        } catch (Exception e) {
            log.warn("DATE_FIELD 回查行失败 table={} dataId={}", tableName, dataId, e);
        }
        return value;
    }

    /**
     * 数据被删除时取消它名下的全部到期提醒任务（P0-5 修复 2 / P2-2）。
     * <p>
     * 原先调用方用 {@code cancelByBizSuffix(orgId, "DESIGNER_DATA", ":" + dataId)}（该方法已随 P2-2 删除），
     * 落地 SQL 是 {@code biz_key LIKE CONCAT('%', #{suffix})} —— 前导通配走不了任何索引，
     * 在每组织 2 万的配额下等于全表扫，且理论上能跨表单命中。这里改为按该表的启用规则
     * 逐个构造完整 key 精确取消（每条都命中 {@code uk_biz} 唯一索引），规则数受"每表单 ≤20"约束。
     * </p>
     *
     * @param tableCode 物理表名（与注册侧同源，取 {@code dynamic_form.table_code}）
     * @param dataId    被删除的数据行 id
     * @return 实际取消的任务行数
     */
    public int cancelForDeletedRow(String tableCode, String dataId) {
        if (StringUtils.isEmpty(tableCode) || StringUtils.isEmpty(dataId)) {
            return 0;
        }
        int cancelled = 0;
        for (DynamicLinkageRule rule : rulesForTable(tableCode)) {
            try {
                if (scheduleTaskClient.cancel(rule.getOrganizationId(), LinkageBizKeys.SOURCE_DATE_FIELD,
                        LinkageBizKeys.dateExpiry(tableCode, rule.getRuleCode(), dataId))) {
                    cancelled++;
                }
            } catch (Exception e) {
                log.error("取消数据到期提醒任务失败 table={} rule={} dataId={}",
                        tableCode, rule.getRuleCode(), dataId, e);
            }
        }
        return cancelled;
    }

    private String bizKey(String tableCode, String ruleCode, String dataId) {
        // P0-2：key 统一由 LinkageBizKeys 构造，注册/改期/取消/级联四条链路同源，
        // 避免"保存钩子一套、每日重算另一套"导致取消打不中、任务只增不减
        return com.privernal.dynamic.advanced.service.LinkageBizKeys.dateExpiry(tableCode, ruleCode, dataId);
    }

    /** 启用中的 DATE_FIELD 规则（按物理表名过滤，60s 缓存） */
    private List<DynamicLinkageRule> rulesForTable(String tableName) {
        CacheEntry entry = ruleCache.get(tableName);
        long now = System.currentTimeMillis();
        if (entry != null && now < entry.expireAt) {
            return entry.rules;
        }
        // assertLinkageIgnoreScope 是"置位不恢复"语义（联动引擎线程内每次触碰 mapper 前重申）；
        // 本方法在 Web 请求线程上内联执行（saveOrUpdate 写后钩子），标志残留会关掉同请求
        // 后续 INSERT 的组织回填（子表行 organization_id NOT NULL 直接失败），并随线程池
        // 污染后续请求——这里 set-then-restore 收口
        Boolean prevIgnoreOrg = com.privernal.common.datascope.aspect.DataScopeHelper.getIgnoreOrganization();
        Boolean prevIgnoreDp = com.privernal.common.datascope.aspect.DataScopeHelper.getIgnoreDataPermission();
        LinkageSupport.assertLinkageIgnoreScope();
        try {
            List<DynamicLinkageRule> all = ruleMapper.selectList(new LambdaQueryWrapper<DynamicLinkageRule>()
                    .eq(DynamicLinkageRule::getEventType, DynamicLinkageRule.EVENT_DATE_FIELD)
                    .eq(DynamicLinkageRule::getEnabled, "Y"));
            List<DynamicLinkageRule> matched = all.stream().filter(rule -> {
                DynamicForm form = linkageSupport.loadForm(rule.getFormId());
                return form != null && tableName.equals(form.getTableCode());
            }).toList();
            ruleCache.put(tableName, new CacheEntry(matched, now + CACHE_TTL_MS));
            return matched;
        } finally {
            com.privernal.common.datascope.aspect.DataScopeHelper.mergeIgnoreStrategy(prevIgnoreOrg, prevIgnoreDp);
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
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd"}) {
            try {
                return new SimpleDateFormat(pattern).parse(s);
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

    private record CacheEntry(List<DynamicLinkageRule> rules, long expireAt) {
    }
}
