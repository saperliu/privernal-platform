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
import com.privernal.common.datascope.aspect.DataScopeHelper;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageQueryMapper;
import com.privernal.dynamic.dto.DynamicDatabaseDTO;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.mapper.DynamicFormMapper;
import com.privernal.dynamic.dbs.service.IDynamicDatabaseService;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 联动引擎公共支撑：表单/业务库解析、目标行安全查询、字段赋值解析。
 * <p>
 * 引擎运行在"系统自动化"语境：显式注入事件携带的组织上下文并忽略部门行级范围，
 * 业务库用 {@code DynamicDataSourceContextHolder} 显式压栈（不依赖会话推导，§5.5 同一口径）。
 */
@Slf4j
@Component
public class LinkageSupport {

    private static final int TARGET_ROW_LIMIT = 1000;

    @Autowired
    private DynamicFormMapper dynamicFormMapper;

    @Autowired
    private IDynamicDatabaseService dynamicDatabaseService;

    @Autowired
    private DynamicLinkageQueryMapper linkageQueryMapper;

    @org.springframework.context.annotation.Lazy
    @Autowired
    private com.privernal.dynamic.dbs.service.DynamicTableService dynamicTableService;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.baomidou.dynamic.datasource.DynamicRoutingDataSource> dataSourceProvider;

    @org.springframework.beans.factory.annotation.Autowired
    private com.privernal.dynamic.dba.dialect.DatabaseDialect databaseDialect;

    /**
     * 组织 → 业务库（数据源名）。dynamic_database.database_name 是物理库名，
     * 不一定是已注册的数据源名（dev 单数据源 master，strict 模式下未知名直接抛错）——
     * 校验数据源注册表，未注册回落 master。
     */
    public String resolveDatabase(String organizationId) {
        return normalizeDatabase(null, organizationId);
    }

    /**
     * 优先使用调用方给定的库名，但必须是已注册数据源；未注册回落组织解析，再回落 master。
     * 事件里的 databaseName 可能来自会话/快照（physical 库名 ≠ 数据源名，strict 模式未知名抛错）。
     */
    public String normalizeDatabase(String preferred, String organizationId) {
        if (StringUtils.isNotEmpty(preferred) && isRegisteredDataSource(preferred)) {
            return preferred;
        }
        return resolveDatabaseInternal(organizationId);
    }

    private boolean isRegisteredDataSource(String name) {
        try {
            com.baomidou.dynamic.datasource.DynamicRoutingDataSource routing =
                    (com.baomidou.dynamic.datasource.DynamicRoutingDataSource) dataSourceProvider.getIfAvailable();
            return routing != null && routing.getDataSources().containsKey(name);
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveDatabaseInternal(String organizationId) {
        DynamicDatabaseDTO config = dynamicDatabaseService.selectDynamicDatabase(organizationId);
        String database = config == null ? null : config.getDatabaseName();
        if (StringUtils.isEmpty(database)) {
            return "master";
        }
        try {
            com.baomidou.dynamic.datasource.DynamicRoutingDataSource routing =
                    (com.baomidou.dynamic.datasource.DynamicRoutingDataSource) dataSourceProvider.getIfAvailable();
            if (routing != null && routing.getDataSources().containsKey(database)) {
                return database;
            }
        } catch (Exception e) {
            log.warn("业务数据源校验失败，回落 master：{}", database, e);
        }
        log.info("组织 {} 登记的库 {} 不是已注册数据源，回落 master", organizationId, database);
        return "master";
    }

    /**
     * TIMER PER_ROW 逐行枚举（§7.1 target_mode=PER_ROW）：按主键升序列出全表行（上限截断），
     * 逐行读回数据后回调。截断打告警（§7.1 max_rows_per_fire 防逐条动作长跑）。
     */
    public void fireTimerRows(com.privernal.dynamic.advanced.domain.DynamicLinkageRule rule, LinkageEvent base,
                              int limit, java.util.function.BiConsumer<String, java.util.Map<String, Object>> consumer) {
        String database = StringUtils.isEmpty(base.getDatabaseName())
                ? resolveDatabase(base.getOrganizationId()) : base.getDatabaseName();
        base.setDatabaseName(database);
        assertLinkageIgnoreScope();
        if (!SafeConditionSql.isValidIdentifier(base.getTableCode())) {
            throw new IllegalArgumentException("非法的联动表编码：" + base.getTableCode());
        }
        String querySql = "SELECT `id` FROM `" + base.getTableCode() + "` WHERE deleted_flag = 'N' "
                + "ORDER BY `id` ASC LIMIT " + (limit + 1);
        List<Map<String, Object>> rows = linkageQueryMapper.selectRows(querySql, java.util.Map.of());
        if (rows.size() > limit) {
            log.warn("TIMER 规则 {} 本轮命中行数超过 max_rows_per_fire={}，已截断（§7.1）",
                    rule.getRuleCode(), limit);
            rows = rows.subList(0, limit);
        }
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            if (id == null) {
                continue;
            }
            String dataId = String.valueOf(id);
            java.util.Map<String, Object> rowData;
            try {
                rowData = dynamicTableService.findById(database, base.getTableCode(), dataId);
            } catch (Exception e) {
                log.warn("TIMER 规则 {} 读行失败 dataId={}", rule.getRuleCode(), dataId, e);
                rowData = null;
            }
            consumer.accept(dataId, rowData);
        }
    }

    /**
     * 重申联动域的忽略策略（组织 + 数据权限同时忽略）。
     * <p>
     * DataScopeHelper 的 ThreadLocal 是整个 IgnoreStrategy 被 setter 整体替换，且联动线程里的
     * 子调用（站内信桥接的本地 feign 链、动态表服务内部的 setIgnore 恢复）都可能覆盖或 clear
     * 掉外层设置的策略——无会话线程一旦丢标志就被数据范围切面兜底 WHERE 1=4 拒绝。
     * 因此引擎内每次直接触碰 mapper 前都要重申，不依赖外层设置存活。
     */
    public static void assertLinkageIgnoreScope() {
        DataScopeHelper.handle(com.privernal.common.datascope.aspect.IgnoreStrategy.builder()
                .organization(true).dataPermission(true).build());
    }

    /** 按主键查业务库单行（软删过滤在 mapper 层），DATE_FIELD 触发/重算读行用 */
    public Map<String, Object> loadRow(String database, String tableCode, String dataId) {
        assertLinkageIgnoreScope();
        try {
            DynamicDataSourceContextHolder.push(database);
            return dynamicTableService.findById(database, tableCode, dataId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /** 按 id 查表单（master 元数据），查不到返回 null */
    public DynamicForm loadForm(String formId) {
        if (StringUtils.isEmpty(formId)) {
            return null;
        }
        assertLinkageIgnoreScope();
        return dynamicFormMapper.selectById(formId);
    }

    /**
     * 目标行定位：条件树 → 安全 WHERE（SafeConditionSql 白名单+绑定值）→ SELECT id。
     * 超过 TARGET_ROW_LIMIT(1000) 直接拒绝执行（§7.4：超限拒绝并告警）。
     *
     * @return 命中主键列表
     */
    /**
     * 动作目标定位条件里的 <code>{{事件字段}}</code> 引用解析（§7.4）。
     * <p>
     * SafeConditionSql 的目标行定位按字面值执行，<code>{{field}}</code> 引用不解析会被
     * normalizeBindValue 置 null 走 IS NULL 语义——静默命中 0 行还报 SUCCESS。因此在定位前
     * 递归把条件叶子的 <code>{{field}}</code> 整值引用替换为事件行字段值；混合拼接（如
     * <code>前缀{{f}}</code>）不在支持范围，保持字面量并沿用原 IS NULL 语义。
     * </p>
     */
    public JSONObject resolveConditionRefs(JSONObject conditionTree, LinkageContext ctx) {
        if (conditionTree == null) {
            return null;
        }
        JSONObject resolved = new JSONObject();
        resolved.putAll(conditionTree);
        com.alibaba.fastjson2.JSONArray children = resolved.getJSONArray("children");
        if (children != null) {
            Map<String, Object> sourceData = ctx.conditionData();
            com.alibaba.fastjson2.JSONArray out = new com.alibaba.fastjson2.JSONArray();
            for (int i = 0; i < children.size(); i++) {
                Object child = children.get(i);
                if (child instanceof JSONObject node) {
                    if (node.containsKey("op")) {
                        out.add(resolveConditionRefs(node, ctx));
                        continue;
                    }
                    if (node.containsKey("cmp")) {
                        Object value = node.get("value");
                        if (value instanceof String s) {
                            String trimmed = s.trim();
                            if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
                                String ref = trimmed.substring(2, trimmed.length() - 2).trim();
                                JSONObject leaf = new JSONObject();
                                leaf.putAll(node);
                                leaf.put("value", ConditionEvaluator.lookup(sourceData, ref));
                                out.add(leaf);
                                continue;
                            }
                        }
                    }
                }
                out.add(child);
            }
            resolved.put("children", out);
        }
        return resolved;
    }

    public List<String> locateTargetIds(String database, String tableName, JSONObject conditionTree) {
        if (!SafeConditionSql.isValidIdentifier(tableName)) {
            throw new IllegalArgumentException("非法的目标表编码：" + tableName);
        }
        Set<String> columns = loadColumnNames(database, tableName);
        Object[] built = SafeConditionSql.build(conditionTree);
        String where = (String) built[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) built[1];
        String whereSql = where == null || where.isEmpty() ? "1=1" : where;
        String querySql = "SELECT `id` FROM `" + tableName + "` WHERE " + whereSql
                + " AND deleted_flag = 'N' LIMIT " + (TARGET_ROW_LIMIT + 1);
        List<Map<String, Object>> rows = queryRows(database, querySql, params);
        if (rows.size() > TARGET_ROW_LIMIT) {
            throw new IllegalStateException("动作目标命中行数超过上限 " + TARGET_ROW_LIMIT + "，拒绝执行（§7.4）");
        }
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            if (id != null) {
                ids.add(String.valueOf(id));
            }
        }
        return ids;
    }

    /** information_schema 校验条件列存在性（第二道防线，缺失列直接报可读错误） */
    public void assertColumnsExist(String database, String tableName, JSONObject conditionTree) {
        List<String> used = new ArrayList<>();
        collectFields(conditionTree, used, 0);
        assertFieldsExist(database, tableName, used, "条件");
    }

    /**
     * information_schema 校验字段列存在性（第二道防线，缺失列直接报可读错误）。
     * 字段编码是设计器口径（连字符/驼峰），列名是下划线小写（toDbColumnName 同口径清洗）——
     * 原值小写、连字符+驼峰归一为下划线，任一命中即存在。
     */
    public void assertFieldsExist(String database, String tableName, java.util.Collection<String> fields, String what) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        Set<String> columns = loadColumnNames(database, tableName);
        for (String field : fields) {
            if (com.privernal.common.core.utils.StringUtils.isEmpty(field)) {
                continue;
            }
            String snake = field.replace('-', '_').replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
            if (!columns.contains(field.toLowerCase()) && !columns.contains(snake)) {
                throw new IllegalArgumentException(what + "引用的列在目标表不存在：" + field);
            }
        }
    }

    private void collectFields(JSONObject node, List<String> used, int depth) {
        if (node == null || depth > ConditionEvaluator.MAX_DEPTH) {
            return;
        }
        if (node.containsKey("field")) {
            used.add(node.getString("field"));
            return;
        }
        var children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                collectFields(children.getJSONObject(i), used, depth + 1);
            }
        }
    }

    private Set<String> loadColumnNames(String database, String tableName) {
        Set<String> columns = new HashSet<>();
        // 方言化元数据查询（information_schema / pragma_table_info），SQL 里无绑定参数
        List<Map<String, Object>> rows = queryRows(database,
                databaseDialect.buildGetTableColumnsSql(tableName), Map.of());
        for (Map<String, Object> row : rows) {
            Object name = row.get("column_name");
            if (name != null) {
                columns.add(String.valueOf(name).toLowerCase());
            }
        }
        return columns;
    }

    private List<Map<String, Object>> queryRows(String database, String querySql, Map<String, Object> params) {
        try {
            // 联动域内重申组织与数据权限同时忽略（information_schema 无组织列，业务查询按规则条件定位）
            assertLinkageIgnoreScope();
            DynamicDataSourceContextHolder.push(database);
            return linkageQueryMapper.selectRows(querySql, params == null ? Map.of() : params);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 动作字段赋值解析（§7.4 SET 字段 = 常量 / 事件字段 / now）：
     * set/fields 数组元素 {field, valueFrom: CONSTANT|EVENT_FIELD|NOW, value}
     */
    public LinkedHashMap<String, Object> resolveFieldValues(com.alibaba.fastjson2.JSONArray fieldMappings,
                                                            LinkageContext ctx) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (fieldMappings == null) {
            return result;
        }
        Map<String, Object> sourceData = ctx.conditionData();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < fieldMappings.size(); i++) {
            JSONObject mapping = fieldMappings.getJSONObject(i);
            String field = mapping.getString("field");
            String valueFrom = mapping.getString("valueFrom");
            Object value = mapping.get("value");
            switch (valueFrom == null ? "CONSTANT" : valueFrom) {
                case "EVENT_FIELD" -> result.put(field, sourceData == null ? null
                        : ConditionEvaluator.lookup(sourceData, value == null ? null : String.valueOf(value)));
                case "NOW" -> result.put(field, format.format(new Date()));
                default -> result.put(field, value);
            }
        }
        return result;
    }

    /**
     * 在事件的组织上下文里执行一段查询（供试跑等复用）
     */
    public <T> T withEventContext(LinkageContext ctx, java.util.function.Supplier<T> supplier) {
        String database = StringUtils.isEmpty(ctx.getDatabaseName())
                ? resolveDatabase(ctx.getEvent().getOrganizationId()) : ctx.getDatabaseName();
        ctx.setDatabaseName(database);
        return supplier.get();
    }
}
