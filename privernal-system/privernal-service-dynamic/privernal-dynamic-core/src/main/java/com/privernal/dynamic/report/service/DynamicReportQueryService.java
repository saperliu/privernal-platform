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

package com.privernal.dynamic.report.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.datav.service.ComputeBucket;
import com.privernal.dynamic.datav.service.ComputeFilterTree;
import com.privernal.dynamic.datav.service.ComputeTopN;
import com.privernal.dynamic.datav.service.ComputeSpecs;
import com.privernal.dynamic.dba.dialect.DatabaseDialect;
import com.privernal.dynamic.dbs.service.DynamicRelationDataService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 报表聚合查询服务：受约束的 AST → SQL，不接受自由 SQL。
 * <p>
 * 链路：解析数据集与过滤 → 列名双重校验（标识符白名单 + 必须在数据集列快照内）
 * → 生成骨架 SELECT 维度,聚合(度量) FROM decorate(tableCode) WHERE deleted_flag='N' AND 过滤
 * GROUP BY … ORDER BY … LIMIT n → 值一律 #{paramMap.pN} 绑定 → 经
 * {@link DynamicReportQueryMapper}（DataScopeMapper 通道）执行，行级范围由数据权限切面
 * 按查看者 dynamic:{tableCode}:list 自动追加（方案A）→ lookup 维度翻译（关联表显示值）。
 * <p>
 * 上限：单图表最多 {@value #MAX_ROWS} 行 / 分组图表默认 {@value #DEFAULT_GROUP_LIMIT} 组 /
 * 查询超时 {@value #TIMEOUT_MILLIS}ms（MySQL MAX_EXECUTION_TIME），超限报可读错误而不是截断静默。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class DynamicReportQueryService {

    /** 单图表结果行数硬上限 */
    public static final int MAX_ROWS = 1000;
    /** 分组图表默认分组数上限 */
    public static final int DEFAULT_GROUP_LIMIT = 50;
    /** 查询超时（毫秒） */
    public static final int TIMEOUT_MILLIS = 10_000;

    /** 聚合函数白名单：保存校验与执行校验共用一份 */
    /**
     * COUNTD = 去重计数。放它进白名单是因为两条路径都能忠实实现：
     * SQL 侧有 COUNT(DISTINCT x)，内存侧靠 HashSet。像 MEDIAN/PERCENTILE 那种
     * MySQL 没有原生函数、只能靠窗口函数凑的聚合，暂不进这个名单——
     * 静默给一个近似值比直接报「不支持」危险得多。
     */
    /** 单一真源在 {@link ComputeSpecs}：两条计算路径必须接受同一组聚合函数，否则同一数据集换个源类型就换个结果 */
    public static final Set<String> AGG_FUNCS = ComputeSpecs.AGG_FUNCS;
    /** 过滤操作符白名单 → SQL 运算符 */
    private static final Map<String, String> FILTER_OPS = Map.of(
            "eq", "=", "ne", "<>", "like", "LIKE", "gt", ">", "ge", ">=", "lt", "<", "le", "<=");
    /** 时间粒度白名单 */
    private static final Set<String> GRANULARITIES = ComputeSpecs.GRANULARITIES;
    /** 表名/列名白名单：标识符经 ${} 拼接进 SQL，必须先行校验防注入（与动态表同口径） */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    /** 图表类型：前四种按分组聚合出图，stat/gauge 为单值卡，table/ranking 直接展示分组行 */
    public static final String TYPE_BAR = "bar";
    public static final String TYPE_PIE = "pie";
    public static final String TYPE_LINE = "line";
    public static final String TYPE_STAT = "stat";
    public static final String TYPE_GAUGE = "gauge";
    public static final String TYPE_RANKING = "ranking";
    public static final String TYPE_TABLE = "table";

    /** 允许的图表类型：保存图表与执行查询共用一份，避免两处白名单各自漂移 */
    public static final Set<String> ALLOWED_TYPES = Set.of(
            TYPE_BAR, TYPE_PIE, TYPE_LINE, TYPE_STAT, TYPE_GAUGE, TYPE_RANKING, TYPE_TABLE);

    /** 无分组单值类型（数字卡 / 仪表盘） */
    private static final Set<String> SINGLE_VALUE_TYPES = Set.of(TYPE_STAT, TYPE_GAUGE);

    /** 单个图表最多几个度量（多系列），超过就要求收窄 */
    public static final int MAX_MEASURES = 5;

    /** 对比模式白名单（M3-1 同比/环比）：yoy=上年同期、mom=上一粒度周期（环比）、prev=上一相邻窗口（单值卡） */
    private static final Set<String> COMPARISON_MODES = Set.of("yoy", "mom", "prev");

    /** lookup 批量回查的单批编码上限 */
    private static final int LOOKUP_BATCH_LIMIT = 500;

    @Autowired
    private DynamicReportExecutor reportExecutor;

    @Autowired
    private DatabaseDialect databaseDialect;

    @Autowired
    private com.privernal.dynamic.report.service.DynamicReportTableResolver tableResolver;

    @Autowired
    private DynamicRelationDataService dynamicRelationDataService;

    /** 多源数据集（M3）：http/excel 等外部源的分派与内存聚合 */
    @Autowired
    private com.privernal.dynamic.datav.service.DatavDataSourceResolver datavDataSourceResolver;

    @Autowired
    private com.privernal.dynamic.datav.service.DatavRowAggregator datavRowAggregator;

    /**
     * 设计态预览：数据集 + 图表配置（可未保存）→ 聚合结果
     *
     * @param database 业务库名（调用方从登录上下文取）
     * @param dataset  数据集
     * @param config   图表配置
     * @return {reportType, rows, limit}
     */
    public Map<String, Object> preview(String database, com.privernal.dynamic.report.domain.DynamicDataset dataset, JSONObject config) {
        return run(database, dataset, config, null);
    }

    /**
     * 设计态预览 + 运行时参数：大屏取数用它把画布参数落成过滤条件，
     * 免得调用方为了「传个参数」去走已保存图表的仪表盘鉴权链路。
     *
     * @param runtimeFilters 运行时过滤 [{field,op,value}]，字段仍要过列快照校验
     */
    public Map<String, Object> preview(String database, com.privernal.dynamic.report.domain.DynamicDataset dataset,
                                       JSONObject config, JSONArray runtimeFilters) {
        return run(database, dataset, config, runtimeFilters);
    }

    /**
     * 运行态查询：已保存图表 + 运行时过滤 → 聚合结果
     *
     * @param database       业务库名
     * @param report         图表
     * @param dataset        图表的数据集
     * @param runtimeFilters 运行时过滤 [{field,op,value}]，可为空
     * @return {reportType, rows, limit}
     */
    public Map<String, Object> query(String database, com.privernal.dynamic.report.domain.DynamicReport report,
                                     com.privernal.dynamic.report.domain.DynamicDataset dataset, JSONArray runtimeFilters) {
        return run(database, dataset, report.getConfig(), runtimeFilters);
    }

    private Map<String, Object> run(String database, com.privernal.dynamic.report.domain.DynamicDataset dataset,
                                    JSONObject config, JSONArray runtimeFilters) {
        if (dataset == null) {
            throw new ServiceException("数据集不存在");
        }
        if (config == null) {
            config = new JSONObject();
        }
        // —— 同比/环比（M3-1）：config.comparison 命中时分派两段聚合 + 内存合并 ——
        JSONObject comparison = config.getJSONObject("comparison");
        if (comparison != null && StringUtils.isNotBlank(comparison.getString("mode"))) {
            return runComparison(database, dataset, config, runtimeFilters, comparison);
        }
        // —— 派生列（M4.2）：定义在数据集列快照里，两条路径共用同一棵 AST ——
        // SQL 路径翻译成表达式下推（动态表不必把整表拉进内存）；内存路径在聚合前补成普通列，
        // 于是聚合器把它当普通字段看待，过滤/分组/聚合都能正常引用。
        Map<String, com.alibaba.fastjson2.JSONObject> derived =
                com.privernal.dynamic.datav.service.DerivedColumnSpec.of(dataset);
        Map<String, com.privernal.dynamic.datav.service.ComputeExpression.Node> derivedNodes =
                com.privernal.dynamic.datav.service.DerivedColumnSpec.validate(derived,
                        com.privernal.dynamic.datav.service.DerivedColumnSpec.baseTypes(dataset, derived));
        // 多源数据集（http/excel/...）：没有 SQL 可下推，拉行后走内存聚合，
        // 输出与本方法后面的 SQL 路径同形，前端与图表组件对源类型无感
        if (datavDataSourceResolver.isExternalSource(dataset.getSourceType())) {
            List<Map<String, Object>> rows = datavDataSourceResolver.fetchRows(
                    dataset.getSourceType(), dataset.getSourceConfig());
            Map<String, String> memoryTypes = parseColumnTypes(dataset);
            Map<String, String> memoryNames = parseColumnNames(dataset);
            // 补派生列必须在过滤与聚合之前：派生列常常正是被过滤/分组的对象（按「利润档」分组）
            com.privernal.dynamic.datav.service.DerivedColumnSpec.applyToRows(rows, derivedNodes, memoryTypes);
            Map<String, Object> outcome = datavRowAggregator.aggregate(rows, memoryTypes, memoryNames,
                    config, runtimeFilters);
            // 字典 lookup 与 SQL 路径同一套实现：外部源存编码、显示名在平台动态表里，
            // 少了这一步，同一份配置换个数据源就会从「老师姓名」退化成「1、2、3」
            JSONArray memoryDims = config.getJSONArray("dimensions");
            translateLookups(database, dataset, memoryDims == null ? new JSONArray() : memoryDims,
                    asResultRows(outcome.get("rows")));
            return outcome;
        }
        String reportType = StringUtils.isNotBlank(config.getString("reportType"))
                ? config.getString("reportType") : TYPE_BAR;
        JSONArray dimensions = config.getJSONArray("dimensions");
        JSONArray measures = config.getJSONArray("measures");
        if (dimensions == null) {
            dimensions = new JSONArray();
        }
        if (measures == null) {
            measures = new JSONArray();
        }

        // 列快照：fieldCode → dataType（数值/时间/文本），另有中文名用于回传图例
        Map<String, String> columnTypes = parseColumnTypes(dataset);
        Map<String, String> columnNames = parseColumnNames(dataset);
        // 逻辑表编码 → 物理表名（组织前缀现场换算，禁止把物理表名写进配置）
        String physicalTable = decorateAndValidate(dataset.getTableCode());

        // —— 白名单关联（M3-2）：快照 joins 段是服务端登记过的关联面，配置里的 joins 键不直接生效 ——
        List<JSONObject> joins = parseJoinMeta(dataset);
        boolean joined = !joins.isEmpty();
        if (joined && datavDataSourceResolver.isExternalSource(dataset.getSourceType())) {
            throw new ServiceException("外部源数据集不支持跨表关联，请用动态表数据集");
        }
        // 主表恒别名 t0（切面注解 mainAlias="t0" 是静态的，别名字段必须无条件存在）：
        // 行级条件带 t0. 前缀，JOIN 出来的第二张表的同名列才不会变成歧义引用
        String mainAlias = "t0";
        // 关联列 → 限定表达式（t1.物理列）：关联列的 fieldCode 带关联字段前缀，与主表列永不撞名
        Map<String, String> joinColumnExpr = new HashMap<>();
        for (int j = 0; j < joins.size(); j++) {
            JSONArray joinColumns = joins.get(j).getJSONArray("columns");
            for (int c = 0; joinColumns != null && c < joinColumns.size(); c++) {
                JSONObject col = joinColumns.getJSONObject(c);
                joinColumnExpr.put(col.getString("fieldCode"),
                        "t" + (j + 1) + "." + requireIdentifier(col.getString("column"), "关联列"));
            }
        }
        java.util.function.Function<String, String> baseExpr = field -> {
            String joinExpr = joinColumnExpr.get(field);
            if (joinExpr != null) {
                return joinExpr;
            }
            String column = requireIdentifier(field, "字段");
            return mainAlias + "." + column;
        };
        java.util.function.Function<String, String> columnExpr = field ->
                com.privernal.dynamic.datav.service.DerivedColumnSpec.sqlExpr(field, derivedNodes, baseExpr);

        Map<String, Object> paramMap = new HashMap<>();
        int[] paramIndex = {0};

        // —— SELECT / GROUP BY ——
        // 别名 → 表达式：HAVING 要按别名引用聚合结果，但拼进 SQL 的必须是原表达式
        // （SQLServer 的 HAVING 不接受 select 别名，用别名会在换库时静默失效）
        Map<String, String> exprByAlias = new LinkedHashMap<>();
        List<String> selectParts = new ArrayList<>();
        List<String> groupParts = new ArrayList<>();
        List<String> dimensionAliases = new ArrayList<>();
        List<String> dimensionLabels = new ArrayList<>();
        for (int i = 0; i < dimensions.size(); i++) {
            JSONObject dim = dimensions.getJSONObject(i);
            String field = requireColumn(dim, columnTypes, "维度");
            String alias = "dim" + i;
            String expr;
            if ("date".equals(columnTypes.get(field)) && StringUtils.isNotBlank(dim.getString("granularity"))) {
                // 时间粒度套在派生列上时，先算表达式再按粒度折
                expr = databaseDialect.buildTimeGroupSql(columnExpr.apply(field),
                        normalizeGranularity(dim.getString("granularity")));
            } else {
                expr = columnExpr.apply(field);
            }
            // 数值分桶：与内存聚合共用 ComputeBucket 的左闭右开语义，这里只翻译成 CASE WHEN
            expr = ComputeBucket.caseWhen(expr, ComputeBucket.parse(dim));
            exprByAlias.put(alias, expr);
            selectParts.add(expr + " AS " + alias);
            groupParts.add(expr);
            dimensionAliases.add(alias);
            dimensionLabels.add(columnNames.getOrDefault(field, field));
        }

        // —— 度量：第一个别名固定为 value（下钻/排序/导出都认它），其余为 m1/m2… ——
        List<String> measureAliases = new ArrayList<>();
        List<String> measureLabels = new ArrayList<>();
        for (int i = 0; i < measures.size(); i++) {
            JSONObject measure = measures.getJSONObject(i);
            if (measure == null) {
                continue;
            }
            String agg = StringUtils.isNotBlank(measure.getString("agg"))
                    ? measure.getString("agg").toUpperCase() : "SUM";
            if (!AGG_FUNCS.contains(agg)) {
                throw new ServiceException("不支持的聚合函数：" + agg + "（" + ComputeSpecs.aggHint() + "）");
            }
            String field = measure.getString("field");
            String aggExpr;
            String label;
            if ("COUNT".equals(agg) && StringUtils.isBlank(field)) {
                // 「计数」语义：不指定字段就是对行计数，不能要求字段
                aggExpr = "COUNT(1)";
                label = "计数";
            } else if ("COUNTD".equals(agg)) {
                String countField = requireColumn(measure, columnTypes, "度量");
                // 去重计数对文本列同样有意义（有多少个不同分类），所以不走数值校验
                aggExpr = "COUNT(DISTINCT " + countField + ")";
                label = columnNames.getOrDefault(countField, countField);
            } else {
                String dataType = columnTypes.get(requireColumn(measure, columnTypes, "度量"));
                if (!"number".equals(dataType)) {
                    throw new ServiceException("度量字段 " + field + " 不是数值列，无法执行 " + agg);
                }
                aggExpr = agg + "(" + columnExpr.apply(field) + ")";
                label = columnNames.getOrDefault(field, field);
            }
            String alias = i == 0 ? "value" : "m" + i;
            exprByAlias.put(alias, aggExpr);
            selectParts.add(aggExpr + " AS " + alias);
            measureAliases.add(alias);
            measureLabels.add(label);
        }
        if (measureAliases.isEmpty()) {
            selectParts.add("COUNT(1) AS value");
            measureAliases.add("value");
            measureLabels.add("计数");
        }
        if (measureAliases.size() > MAX_MEASURES) {
            throw new ServiceException("单个图表最多 " + MAX_MEASURES + " 个度量，当前 " + measureAliases.size() + " 个");
        }

        boolean singleValue = SINGLE_VALUE_TYPES.contains(reportType);
        if (!singleValue && dimensionAliases.isEmpty()) {
            throw new ServiceException("该图表类型至少需要一个维度字段");
        }

        // —— WHERE：固定行 + 数据集默认过滤 + 图表过滤 + 运行时过滤 ——
        List<String> whereParts = new ArrayList<>();
        whereParts.add(mainAlias + ".deleted_flag = 'N'");
        // 数据集默认过滤存储形态：{"filters":[{field,op,value}]}
        JSONObject defaultFilter = dataset.getDefaultFilter();
        appendFilters(whereParts, paramMap, paramIndex,
                defaultFilter == null ? null : defaultFilter.getJSONArray("filters"), columnTypes, "数据集默认过滤",
                columnExpr);
        // 图表侧支持 AND/OR 嵌套树；旧的平铺 filters 按 and 解释，所以历史配置行为不变
        appendFilterTree(whereParts, paramMap, paramIndex,
                ComputeFilterTree.normalize(config.getJSONObject("filterTree"), config.getJSONArray("filters")),
                field -> {
                    requireColumn(field, columnTypes, "过滤");
                    return columnExpr.apply(field);
                }, "过滤");
        appendFilters(whereParts, paramMap, paramIndex, runtimeFilters, columnTypes, "运行时过滤", columnExpr);

        // —— ORDER BY / LIMIT ——
        if (!ALLOWED_TYPES.contains(reportType)) {
            throw new ServiceException("不支持的图表类型：" + reportType);
        }
        String orderClause;
        int limit;
        // 实际取回多少组。默认与 limit 相同；配了「其他」归并时必须放大到上限，
        // 否则"其他"只汇总了被 LIMIT 砍剩的那几组——数字看着合理，其实是错的
        int fetchLimit;
        JSONObject topN = config.getJSONObject("topN");
        boolean mergeRest = topN != null && topN.getIntValue("n") > 0
                && StringUtils.isNotBlank(topN.getString("mergeRestAs"));
        if (singleValue) {
            orderClause = "";
            limit = 1;
            fetchLimit = 1;
        } else {
            int configLimit = config.getIntValue("limit");
            limit = configLimit > 0 ? Math.min(configLimit, MAX_ROWS) : DEFAULT_GROUP_LIMIT;
            fetchLimit = limit;
            orderClause = buildOrderBy(config, dimensionAliases, measureAliases);
            if (mergeRest && !groupParts.isEmpty()) {
                selectParts.add("COUNT(1) AS " + ComputeTopN.WEIGHT_ALIAS);
                exprByAlias.put(ComputeTopN.WEIGHT_ALIAS, "COUNT(1)");
                fetchLimit = MAX_ROWS;
            }
        }

        String querySql = "SELECT" + databaseDialect.buildTimeoutHint(TIMEOUT_MILLIS) + " "
                + String.join(", ", selectParts)
                + " FROM " + buildFromClause(physicalTable, joins)
                + " WHERE " + String.join(" AND ", whereParts);
        if (!groupParts.isEmpty()) {
            querySql += " GROUP BY " + String.join(", ", groupParts);
        }
        // HAVING 必须下推到库里：先聚合再在 Java 侧筛，等于把 1000 行上限用在筛之前，
        // 用户会看到「筛完没数据」而实际数据是存在的
        List<String> havingParts = new ArrayList<>();
        appendFilterTree(havingParts, paramMap, paramIndex,
                ComputeFilterTree.normalize(config.getJSONObject("having"), config.getJSONArray("havingFilters")),
                alias -> havingExprByAlias(alias, exprByAlias), "聚合过滤");
        if (!havingParts.isEmpty()) {
            querySql += " HAVING " + String.join(" AND ", havingParts);
        }
        querySql += orderClause;
        if (!singleValue) {
            querySql += databaseDialect.buildLimitClause(fetchLimit + 1);
        }

        // 走 executor 而不是直调 mapper：这一层负责把连接切到组织业务库
        List<Map<String, Object>> rows = reportExecutor.aggregate(database, physicalTable, querySql, paramMap);

        // 无 GROUP BY 的聚合在无匹配行时返回一行全 NULL（MyBatis 对全空行给 null 元素），数字卡显示为 0
        if (singleValue && rows != null && !rows.isEmpty()
                && (rows.get(0) == null || rows.get(0).get("value") == null)) {
            List<Map<String, Object>> normalized = new ArrayList<>();
            Map<String, Object> zero = new HashMap<>();
            zero.put("value", 0);
            normalized.add(zero);
            return finishResult(reportType, limit, normalized, dimensionAliases, dimensionLabels, measureAliases, measureLabels);
        }

        if (rows != null && rows.size() > fetchLimit) {
            throw new ServiceException("聚合结果超过上限 " + fetchLimit + " 组，请增加过滤条件或收窄维度");
        }

        // —— TopN 与「其他」归并：与内存聚合路径共用同一段后处理 ——
        if (topN != null && rows != null && !rows.isEmpty() && !groupParts.isEmpty()) {
            List<JSONObject> objects = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                objects.add(row instanceof JSONObject object ? object : new JSONObject(row));
            }
            ComputeTopN.apply(objects, topN, aliasAggByMeasure(exprByAlias, measureAliases, config),
                    dimensionAliases.get(0));
            objects.forEach(row -> row.remove(ComputeTopN.WEIGHT_ALIAS));
            rows = new ArrayList<>(objects);
        }

        // —— lookup 维度翻译（M5）：关联表显示值替换维度编码 ——
        translateLookups(database, dataset, dimensions, rows);

        return finishResult(reportType, limit, rows, dimensionAliases, dimensionLabels, measureAliases, measureLabels);
    }

    /**
     * 白名单关联（M3-2）：读数据集列快照的 joins 段（服务端在数据集构建/保存时按登记关系
     * 重解析落定，模型配置里的 joins 键不直接生效），逐条复核标识符防手改快照绕过校验。
     */
    private List<JSONObject> parseJoinMeta(com.privernal.dynamic.report.domain.DynamicDataset dataset) {
        JSONObject snapshot = dataset.getColumnSnapshot();
        JSONArray joinMeta = snapshot == null ? null : snapshot.getJSONArray("joins");
        if (joinMeta == null || joinMeta.isEmpty()) {
            return new ArrayList<>();
        }
        List<JSONObject> joins = new ArrayList<>();
        for (int i = 0; i < joinMeta.size(); i++) {
            JSONObject join = joinMeta.getJSONObject(i);
            if (join == null) {
                continue;
            }
            String type = StringUtils.trimToEmpty(join.getString("type"));
            if (!"subTable".equals(type) && !"relation".equals(type)) {
                throw new ServiceException("不支持的关联类型：" + type + "（subTable|relation）");
            }
            requireIdentifier(join.getString("field"), "关联字段");
            requireIdentifier(join.getString("physicalTable"), "关联表");
            joins.add(join);
        }
        return joins;
    }

    /**
     * FROM 子句：主表恒别名 t0（数据范围切面 mainAlias="t0" 按别名限定行级条件，必须无条件存在）；
     * 有关联时追加白名单 INNER JOIN——子表 ON parent_id，引用关联 ON 目标表 id = 主表.关联字段，
     * 关联表软删条件进 ON（进 WHERE 会在 OR 树里改变语义）。
     */
    static String buildFromClause(String physicalTable, List<JSONObject> joins) {
        StringBuilder from = new StringBuilder(physicalTable).append(" t0");
        if (joins == null || joins.isEmpty()) {
            return from.toString();
        }
        for (int i = 0; i < joins.size(); i++) {
            JSONObject join = joins.get(i);
            String alias = "t" + (i + 1);
            String on = "subTable".equals(join.getString("type"))
                    ? alias + ".parent_id = t0.id"
                    : alias + ".id = t0." + join.getString("field");
            from.append(" INNER JOIN ").append(join.getString("physicalTable")).append(' ').append(alias)
                    .append(" ON ").append(on).append(" AND ").append(alias).append(".deleted_flag = 'N'");
        }
        return from.toString();
    }

    /**
     * 同比/环比（M3-1）：两段聚合 + Java 内存合并，方言层零改动。
     * <p>
     * 模式：yoy=上年同期（分组键减 1 年）、mom=上一粒度周期（环比，键减 1 个粒度）、
     * prev=上一相邻窗口（单值卡：本期过滤窗口整体平移一窗）。yoy/mom 要求维度里有一个
     * date 型带 granularity 的维度做对齐键；prev 要求 stat/gauge 单值 + 时间范围过滤。
     * 对比段的时间过滤按同样偏移平移（只是剪枝，对齐靠分组键的「解析→平移→规范化」，
     * 与方言输出格式解耦：MySQL/PG/SQLServer 的 WEEK/HOUR 格式差异被归一掉）。
     * <p>
     * 输出：rows 每行追加 {valueCompare,m1Compare…} 与 {valueGrowth,m1Growth…}；
     * growth=(cur−cmp)/cmp，cmp 缺失或为 0 时为 null（前端与模型叙述按「—」处理）。
     * measureAliases 追加 Compare 系列（ChartCard 多系列直接可画），growth 不进系列只留在 rows。
     */
    Map<String, Object> runComparison(String database, com.privernal.dynamic.report.domain.DynamicDataset dataset,
                                               JSONObject config, JSONArray runtimeFilters, JSONObject comparison) {
        String mode = comparison.getString("mode").trim().toLowerCase();
        if (!COMPARISON_MODES.contains(mode)) {
            throw new ServiceException("不支持的对比模式：" + mode + "（yoy|mom|prev）");
        }
        if (datavDataSourceResolver.isExternalSource(dataset.getSourceType())) {
            throw new ServiceException("外部源数据集暂不支持同比/环比对比，请用动态表数据集");
        }
        // 深拷贝去 comparison 键：两段子查询都走 run() 原路径，防递归
        JSONObject baseConfig = JSONObject.from(config);
        baseConfig.remove("comparison");

        JSONArray dimensions = baseConfig.getJSONArray("dimensions");
        Map<String, String> columnTypes = parseColumnTypes(dataset);
        // 对齐维度（yoy/mom）：第一个 date 型带 granularity 的维度
        int timeDimIndex = -1;
        String timeDimField = null;
        String granularity = null;
        if (dimensions != null) {
            for (int i = 0; i < dimensions.size(); i++) {
                JSONObject dim = dimensions.getJSONObject(i);
                String field = dim == null ? null : dim.getString("field");
                if (field != null && "date".equals(columnTypes.get(field))
                        && StringUtils.isNotBlank(dim.getString("granularity"))) {
                    timeDimIndex = i;
                    timeDimField = field;
                    granularity = normalizeGranularity(dim.getString("granularity"));
                    break;
                }
            }
        }
        if ("yoy".equals(mode) || "mom".equals(mode)) {
            if (timeDimIndex < 0) {
                throw new ServiceException("同比/环比需要至少一个带时间粒度（granularity）的日期维度做对齐键");
            }
            if ("yoy".equals(mode) && "YEAR".equals(granularity)) {
                throw new ServiceException("年粒度没有更上一级周期，上年同期请改用月/日粒度或 prev");
            }
            if ("mom".equals(mode) && "YEAR".equals(granularity)) {
                throw new ServiceException("年粒度的环比即同比，请用 yoy");
            }
        }
        // 平移单位：yoy=1 年；mom=1 个粒度周期；prev 整窗平移不用单位（granularity 此时可为 null）
        java.time.temporal.ChronoUnit shiftUnit = null;
        if (!"prev".equals(mode)) {
            shiftUnit = "yoy".equals(mode) ? java.time.temporal.ChronoUnit.YEARS : granularityUnit(granularity);
        }

        JSONArray cmpRuntimeFilters = runtimeFilters == null ? null : JSONArray.from(runtimeFilters);
        JSONObject cmpConfig = JSONObject.from(baseConfig);
        if ("prev".equals(mode)) {
            CompareWindow window = applyPrevWindowShift(cmpConfig, columnTypes, dataset);
            if (cmpRuntimeFilters != null) {
                shiftRuntimeFiltersByWindow(cmpRuntimeFilters, window.field(), window.length());
            }
        } else {
            // 对比期=本期之前一个周期：过滤窗口按同一单位回移 1 格（对齐时分组键再前移回来）
            applyTimeFilterShift(cmpConfig, timeDimField, -1, shiftUnit);
            if (cmpRuntimeFilters != null && timeDimField != null) {
                shiftFlatFilters(cmpRuntimeFilters, timeDimField, -1, shiftUnit);
            }
        }

        Map<String, Object> current = run(database, dataset, baseConfig, runtimeFilters);
        Map<String, Object> compare = run(database, dataset, cmpConfig, cmpRuntimeFilters);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> curRows = current.get("rows") instanceof List
                ? (List<Map<String, Object>>) current.get("rows") : new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cmpRows = compare.get("rows") instanceof List
                ? (List<Map<String, Object>>) compare.get("rows") : new ArrayList<>();
        if (curRows.isEmpty()) {
            return current;
        }

        @SuppressWarnings("unchecked")
        List<String> measureAliases = (List<String>) current.get("measureAliases");
        @SuppressWarnings("unchecked")
        List<String> measureLabels = (List<String>) current.get("measureLabels");
        if ("prev".equals(mode)) {
            // 单值卡：对比窗口的 value（与 m1…）直接挂到本期唯一一行
            Map<String, Object> cmpRow = cmpRows.isEmpty() ? null : cmpRows.get(0);
            for (String alias : measureAliases) {
                Object cmpValue = cmpRow == null ? null : cmpRow.get(alias);
                curRows.get(0).put(alias + "Compare", cmpValue);
                curRows.get(0).put(alias + "Growth", growthOf(curRows.get(0).get(alias), cmpValue));
            }
        } else {
            // 分组序列：对比段行的分组键平移回本期坐标系后逐行对齐（其他维度值参与 join）
            Map<String, Map<String, Object>> cmpByKey = new HashMap<>();
            for (Map<String, Object> row : cmpRows) {
                String key = comparisonAlignKey(row, dimensions, timeDimIndex, granularity, 1, shiftUnit);
                if (key != null) {
                    cmpByKey.put(key, row);
                }
            }
            for (Map<String, Object> row : curRows) {
                String key = comparisonAlignKey(row, dimensions, timeDimIndex, granularity, 0, shiftUnit);
                Map<String, Object> cmpRow = key == null ? null : cmpByKey.get(key);
                for (String alias : measureAliases) {
                    Object cmpValue = cmpRow == null ? null : cmpRow.get(alias);
                    row.put(alias + "Compare", cmpValue);
                    row.put(alias + "Growth", growthOf(row.get(alias), cmpValue));
                }
            }
        }
        // measureAliases 追加 Compare 系列（labels 同步）；Growth 只留在 rows 不进系列
        List<String> aliasesOut = new ArrayList<>(measureAliases);
        List<String> labelsOut = new ArrayList<>(measureLabels);
        for (int i = 0; i < measureAliases.size(); i++) {
            aliasesOut.add(measureAliases.get(i) + "Compare");
            labelsOut.add(measureLabels.get(i) + "(对比)");
        }
        current.put("measureAliases", aliasesOut);
        current.put("measureLabels", labelsOut);
        current.put("comparison", mode);
        return current;
    }

    /** mom 模式的平移单位由对齐粒度决定 */
    private java.time.temporal.ChronoUnit granularityUnit(String granularity) {
        return switch (granularity) {
            case "MONTH" -> java.time.temporal.ChronoUnit.MONTHS;
            case "WEEK" -> java.time.temporal.ChronoUnit.WEEKS;
            case "DAY" -> java.time.temporal.ChronoUnit.DAYS;
            case "HOUR" -> java.time.temporal.ChronoUnit.HOURS;
            case "MINUTE" -> java.time.temporal.ChronoUnit.MINUTES;
            default -> throw new ServiceException("该时间粒度不支持环比：" + granularity);
        };
    }

    /**
     * 把 config 里的时间过滤按「value 平移 amount 个单位」改写。
     * 只从归一树单遍平移：{@link ComputeFilterTree#normalize} 会把平铺 filters 以同一批
     * 叶子对象并进树里，先平移平铺再遍历树会把同一片叶子平移两次。
     */
    private void applyTimeFilterShift(JSONObject config, String field, long amount, java.time.temporal.ChronoUnit unit) {
        JSONObject tree = ComputeFilterTree.normalize(config.getJSONObject("filterTree"), config.getJSONArray("filters"));
        shiftTreeFilters(tree, field, amount, unit);
    }

    private void shiftFlatFilters(JSONArray filters, String field, long amount, java.time.temporal.ChronoUnit unit) {
        if (filters == null) {
            return;
        }
        for (int i = 0; i < filters.size(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            if (filter != null && field.equals(filter.getString("field"))) {
                shiftFilterValue(filter, amount, unit);
            }
        }
    }

    private void shiftTreeFilters(JSONObject node, String field, long amount, java.time.temporal.ChronoUnit unit) {
        if (node == null) {
            return;
        }
        if (ComputeFilterTree.isGroup(node)) {
            JSONArray when = node.getJSONArray("when");
            if (when != null) {
                for (int i = 0; i < when.size(); i++) {
                    JSONObject child = when.getJSONObject(i);
                    if (child != null) {
                        shiftTreeFilters(child, field, amount, unit);
                    }
                }
            }
            return;
        }
        if (field.equals(node.getString("field"))) {
            shiftFilterValue(node, amount, unit);
        }
    }

    private void shiftFilterValue(JSONObject filter, long amount, java.time.temporal.ChronoUnit unit) {
        String op = ComputeFilterTree.leafOp(filter);
        if (!Set.of("eq", "gt", "ge", "lt", "le").contains(op)) {
            return;
        }
        Object value = filter.get("value");
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return;
        }
        java.time.LocalDateTime parsed = parseFlexibleDateTime(String.valueOf(value).trim());
        if (parsed == null) {
            throw new ServiceException("同比/环比的时间过滤值无法解析：" + value + "（请用 yyyy-MM-dd 或完整时间格式）");
        }
        filter.put("value", parsed.plus(amount, unit)
                .format(java.time.format.DateTimeFormatter.ofPattern(
                        parsed.getNano() == 0 && parsed.getSecond() == 0 && parsed.getMinute() == 0 && parsed.getHour() == 0
                                ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss")));
    }

    /** prev 对比窗口：平移所依据的字段与窗口长度（供运行时过滤同窗平移） */
    private record CompareWindow(String field, java.time.Duration length) {
    }

    /**
     * prev 模式：从平铺 filters 里找第一个 date 型字段的 [ge,le]（或 gt/lt）窗口，
     * 把窗口整体平移一个窗口长度（对比窗口 = 紧挨本期之前的同长窗口）。
     * 返回检测到的窗口，供运行时过滤做同样平移。
     */
    private CompareWindow applyPrevWindowShift(JSONObject config, Map<String, String> columnTypes,
                                               com.privernal.dynamic.report.domain.DynamicDataset dataset) {
        JSONArray filters = config.getJSONArray("filters");
        if (filters == null) {
            filters = new JSONArray();
            config.put("filters", filters);
        }
        String windowField = null;
        java.time.LocalDateTime start = null;
        java.time.LocalDateTime end = null;
        String endOp = null;
        for (int i = 0; i < filters.size(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            String field = filter == null ? null : filter.getString("field");
            String op = filter == null ? null : ComputeFilterTree.leafOp(filter);
            if (field == null || !"date".equals(columnTypes.get(field))) {
                continue;
            }
            java.time.LocalDateTime value = parseFlexibleDateTime(String.valueOf(filter.get("value")).trim());
            if (value == null) {
                continue;
            }
            if (windowField == null) {
                windowField = field;
            }
            if (!field.equals(windowField)) {
                continue;
            }
            if (Set.of("ge", "gt", "eq").contains(op) && start == null) {
                start = value;
            } else if (Set.of("le", "lt").contains(op) && end == null) {
                end = value;
                endOp = op;
            }
        }
        if (windowField == null || start == null || end == null) {
            throw new ServiceException("上一期对比（prev）需要某个日期字段的起止范围过滤（ge+le），如「本月」");
        }
        if (end.isBefore(start)) {
            throw new ServiceException("时间范围过滤的结束值早于开始值");
        }
        java.time.Duration window = java.time.Duration.between(start, end);
        if (window.isZero()) {
            // 「今天」这类单点窗口（ge+le 同一天）按一天长处理，上一期=昨天；开区间零窗才是真错误
            if ("lt".equals(endOp)) {
                throw new ServiceException("上一期对比的时间窗口为零长度");
            }
            window = java.time.Duration.ofDays(1);
        }
        for (int i = 0; i < filters.size(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            String field = filter == null ? null : filter.getString("field");
            String op = filter == null ? null : ComputeFilterTree.leafOp(filter);
            if (field == null || !field.equals(windowField) || op == null) {
                continue;
            }
            java.time.LocalDateTime value = parseFlexibleDateTime(String.valueOf(filter.get("value")).trim());
            if (value == null) {
                continue;
            }
            java.time.LocalDateTime shifted = value.minus(window);
            filter.put("value", shifted.format(java.time.format.DateTimeFormatter.ofPattern(
                    shifted.getHour() == 0 && shifted.getMinute() == 0 && shifted.getSecond() == 0
                            ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss")));
        }
        // prev 只对单值卡有意义：分组序列的对比窗口键与本期不重合，逐行对齐无从谈起
        String reportType = StringUtils.isNotBlank(config.getString("reportType"))
                ? config.getString("reportType") : TYPE_BAR;
        if (!SINGLE_VALUE_TYPES.contains(reportType)) {
            throw new ServiceException("上一期对比（prev）只支持 stat/gauge 单值卡；分组趋势请用 yoy/mom");
        }
        return new CompareWindow(windowField, window);
    }

    /**
     * prev 模式的运行时过滤（大屏参数）按对比窗口同样平移。
     * 与图表过滤不同：解析不了的值原样保留（运行时参数可能不是日期），不在静默改写里猜格式。
     */
    private void shiftRuntimeFiltersByWindow(JSONArray filters, String field, java.time.Duration window) {
        for (int i = 0; i < filters.size(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            String f = filter == null ? null : filter.getString("field");
            if (f == null || !f.equals(field)) {
                continue;
            }
            Object value = filter.get("value");
            if (value == null || StringUtils.isBlank(String.valueOf(value))) {
                continue;
            }
            java.time.LocalDateTime parsed = parseFlexibleDateTime(String.valueOf(value).trim());
            if (parsed == null) {
                continue;
            }
            java.time.LocalDateTime shifted = parsed.minus(window);
            filter.put("value", shifted.format(java.time.format.DateTimeFormatter.ofPattern(
                    shifted.getHour() == 0 && shifted.getMinute() == 0 && shifted.getSecond() == 0
                            ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss")));
        }
    }

    /** 宽松时间解析：yyyy-MM-dd / yyyy-MM-dd HH:mm[:ss] / yyyy-MM / yyyy（不够精确的按月初/年初零点） */
    private java.time.LocalDateTime parseFlexibleDateTime(String value) {
        try {
            return java.time.LocalDate.parse(value).atStartOfDay();
        } catch (Exception ignored) {
            // 继续尝试更长的格式
        }
        String normalized = value.replace(' ', 'T');
        try {
            return java.time.LocalDateTime.parse(normalized.length() == 16 ? normalized + ":00" : normalized);
        } catch (Exception ignored) {
            // 继续尝试月份/年份格式
        }
        try {
            return java.time.YearMonth.parse(value).atDay(1).atStartOfDay();
        } catch (Exception ignored) {
            // 继续尝试年份
        }
        try {
            return java.time.Year.parse(value).atDay(1).atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 对齐键：行的时间维度值（分组字符串）解析 → 按偏移平移 → 规范化成 java.time 对象
     * （equals/hashCode 可靠），再与其他维度值拼成 join 键。两段用同一函数，仅偏移量不同——
     * 方言输出格式差异（SQLServer 的 2026-W43、PG 的小时无 :00）在解析时归一。
     */
    String comparisonAlignKey(Map<String, Object> row, JSONArray dimensions, int timeDimIndex,
                                      String granularity, long shift, java.time.temporal.ChronoUnit unit) {
        Object raw = row.get("dim" + timeDimIndex);
        if (raw == null) {
            return null;
        }
        java.time.temporal.Temporal key = parseGroupKey(String.valueOf(raw), granularity);
        if (key == null) {
            return null;
        }
        if (shift != 0) {
            key = key.plus(shift, unit);
        }
        StringBuilder joinKey = new StringBuilder(String.valueOf(key));
        if (dimensions != null) {
            for (int i = 0; i < dimensions.size(); i++) {
                if (i != timeDimIndex) {
                    joinKey.append('\u0001').append(row.get("dim" + i));
                }
            }
        }
        return joinKey.toString();
    }

    /** 分组键解析：按粒度把方言格式化的分组字符串还原为 java.time 对象 */
    static java.time.temporal.Temporal parseGroupKey(String value, String granularity) {
        String s = value.trim();
        try {
            return switch (granularity) {
                case "YEAR" -> java.time.Year.parse(s);
                case "MONTH" -> java.time.YearMonth.parse(s);
                case "DAY" -> java.time.LocalDate.parse(s);
                // WEEK：MySQL/PG 是「ISO年-周」(2026-43)，SQLServer 带 W 前缀(2026-W43)；剥前缀统一。
                // 1 月 4 日恒在 ISO 第 1 周，用它做锚点把 (年,周) 还原成 LocalDate
                case "WEEK" -> {
                    String[] parts = s.replace("W", "").split("-");
                    if (parts.length != 2) {
                        yield null;
                    }
                    yield java.time.LocalDate.of(Integer.parseInt(parts[0]), 1, 4)
                            .with(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear(), Long.parseLong(parts[1]));
                }
                // HOUR：MySQL/SQLServer 带分钟占位(09:00)、PG 不带(09)——统一截到 yyyy-MM-ddTHH 再补分钟
                case "HOUR" -> java.time.LocalDateTime.parse(s.replace(' ', 'T').substring(0, 13) + ":00");
                case "MINUTE" -> java.time.LocalDateTime.parse(s.replace(' ', 'T').substring(0, 16));
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /** growth=(cur−cmp)/cmp；cmp 缺失或为 0 时返回 null（「—」），不做近似 */
    static Object growthOf(Object current, Object compare) {
        if (current == null || compare == null) {
            return null;
        }
        try {
            java.math.BigDecimal cur = toDecimal(current);
            java.math.BigDecimal cmp = toDecimal(compare);
            if (cmp.signum() == 0) {
                return null;
            }
            return cur.subtract(cmp).divide(cmp, 4, java.math.RoundingMode.HALF_UP).doubleValue();
        } catch (Exception e) {
            return null;
        }
    }

    private static java.math.BigDecimal toDecimal(Object value) {
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new java.math.BigDecimal(String.valueOf(number));
        }
        return new java.math.BigDecimal(String.valueOf(value));
    }

    /** 聚合结果的 rows 是 List<JSONObject>，这里只做类型适配，找不到就返回空表避免 NPE */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asResultRows(Object rows) {
        return rows instanceof List ? (List<Map<String, Object>>) rows : new ArrayList<>();
    }

    /**
     * 排序：只允许按本图表的维度/别名排序，默认按第一个度量倒序。
     * 别名与维度都来自服务内部生成的白名单集合，不接受任意列名。
     */
    private String buildOrderBy(JSONObject config, List<String> dimensionAliases, List<String> measureAliases) {
        JSONObject sort = config.getJSONObject("sort");
        if (sort == null) {
            return " ORDER BY " + measureAliases.get(0) + " DESC";
        }
        String field = sort.getString("field");
        String order = StringUtils.isBlank(sort.getString("order")) ? "DESC" : sort.getString("order").toUpperCase();
        if (!"DESC".equals(order) && !"ASC".equals(order)) {
            throw new ServiceException("排序方向只能是 ASC 或 DESC：" + order);
        }
        if (StringUtils.isBlank(field)) {
            return " ORDER BY " + measureAliases.get(0) + " " + order;
        }
        if (!dimensionAliases.contains(field) && !measureAliases.contains(field)) {
            throw new ServiceException("排序字段必须是本图表的维度或度量：" + field);
        }
        return " ORDER BY " + field + " " + order;
    }

    /**
     * 返回体带上维度/度量别名与中文字段名：前端渲染多系列、列头、导出表头时不必再从列名里猜，
     * 也没有别的途径拿到列快照。
     */
    private Map<String, Object> finishResult(String reportType, int limit, List<Map<String, Object>> rows,
                                             List<String> dimensionAliases, List<String> dimensionLabels,
                                             List<String> measureAliases, List<String> measureLabels) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportType", reportType);
        result.put("limit", limit);
        result.put("rows", rows);
        result.put("dimensionAliases", dimensionAliases);
        result.put("dimensionLabels", dimensionLabels);
        result.put("measureAliases", measureAliases);
        result.put("measureLabels", measureLabels);
        return result;
    }

    /**
     * lookup 维度翻译：维度配置 lookup:{targetTableCode, displayField} 时，把分组出的编码
     * 批量回查被关联表显示列。授权口径与关联显示数据一致（DynamicRelationDataService：
     * 引用方登记放行，按 return_columns 收敛）；查不到授权则保留原编码不翻译。
     */
    private void translateLookups(String database, com.privernal.dynamic.report.domain.DynamicDataset dataset,
                                  JSONArray dimensions, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (int i = 0; i < dimensions.size(); i++) {
            JSONObject dim = dimensions.getJSONObject(i);
            JSONObject lookup = dim.getJSONObject("lookup");
            if (lookup == null) {
                continue;
            }
            String targetTableCode = lookup.getString("targetTableCode");
            String displayField = lookup.getString("displayField");
            if (StringUtils.isBlank(targetTableCode) || StringUtils.isBlank(displayField)) {
                continue;
            }
            if (!IDENTIFIER_PATTERN.matcher(targetTableCode).matches()
                    || !IDENTIFIER_PATTERN.matcher(displayField).matches()) {
                log.warn("lookup 配置含非法标识符，跳过翻译：{} / {}", targetTableCode, displayField);
                continue;
            }
            String alias = "dim" + i;
            Set<String> codes = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Object code = row.get(alias);
                if (code != null) {
                    codes.add(String.valueOf(code));
                }
            }
            if (codes.isEmpty()) {
                continue;
            }
            try {
                String physicalTarget = decorateAndValidate(targetTableCode);
                // 授权：查看者对被关联表有 list 权限，或引用方（数据集底层表）登记了关联且收敛列包含显示列
                DynamicRelationDataService.RelationReadGrant grant = dynamicRelationDataService
                        .resolveReadGrant(physicalTarget, decorateAndValidate(dataset.getTableCode()), null);
                if (grant.getVisibleColumns() != null && !grant.getVisibleColumns().contains(displayField)) {
                    log.info("lookup 显示列 {} 不在引用授权列内，保留编码返回", displayField);
                    continue;
                }
                Map<String, String> displayMap = queryDisplayValues(database, physicalTarget, displayField, codes);
                for (Map<String, Object> row : rows) {
                    Object code = row.get(alias);
                    if (code != null && displayMap.containsKey(String.valueOf(code))) {
                        row.put(alias, displayMap.get(String.valueOf(code)));
                        // 保留原始编码供下钻使用
                        row.put(alias + "Code", code);
                    }
                }
            } catch (Exception e) {
                log.warn("lookup 翻译失败，保留编码返回：{}", e.getMessage());
            }
        }
    }

    /**
     * 批量回查显示值：一次 IN 查询替代逐条 findById 的 N+1（50 组分组原先要 50 次单行查询）。
     * 首参是目标表名，所以行级范围仍由切面按查看者追加——翻不出来的编码保持原值，不会泄露显示列。
     */
    private Map<String, String> queryDisplayValues(String database, String physicalTarget,
                                                   String displayField, Set<String> codes) {
        List<String> codeList = new ArrayList<>(codes);
        Map<String, String> displayMap = new HashMap<>();
        for (int from = 0; from < codeList.size(); from += LOOKUP_BATCH_LIMIT) {
            List<String> batch = codeList.subList(from, Math.min(from + LOOKUP_BATCH_LIMIT, codeList.size()));
            List<String> placeholders = new ArrayList<>();
            Map<String, Object> paramMap = new HashMap<>();
            for (int i = 0; i < batch.size(); i++) {
                String name = "c" + i;
                placeholders.add("#{paramMap." + name + "}");
                paramMap.put(name, batch.get(i));
            }
            // 目标表恒别名 t0：数据范围切面按 mainAlias=t0 限定列（与聚合主表同口径），
            // 不加别名会让切面拼出 t0.department_id 而查无此表
            String sql = "SELECT" + databaseDialect.buildTimeoutHint(TIMEOUT_MILLIS)
                    + " id AS k, " + displayField + " AS v FROM " + physicalTarget + " t0"
                    + " WHERE deleted_flag = 'N' AND id IN (" + String.join(", ", placeholders) + ")";
            for (Map<String, Object> row : reportExecutor.lookupDisplay(database, physicalTarget, sql, paramMap)) {
                Object key = row.get("k");
                Object value = row.get("v");
                if (key != null && value != null) {
                    displayMap.put(String.valueOf(key), String.valueOf(value));
                }
            }
        }
        return displayMap;
    }

    private void appendFilters(List<String> whereParts, Map<String, Object> paramMap, int[] paramIndex,
                               JSONArray filters, Map<String, String> columnTypes, String source,
                               java.util.function.Function<String, String> columnExpr) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        for (int i = 0; i < filters.size(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            if (filter == null) {
                continue;
            }
            String field = filter.getString("field");
            String op = filter.getString("op");
            Object value = filter.get("value");
            if (StringUtils.isBlank(field) || value == null || "".equals(value)) {
                continue;
            }
            requireColumn(filter, columnTypes, source);
            String sqlOp = FILTER_OPS.get(op == null ? "eq" : op.toLowerCase());
            if (sqlOp == null) {
                throw new ServiceException("不支持的过滤操作符：" + op);
            }
            String paramName = "p" + paramIndex[0]++;
            if ("LIKE".equals(sqlOp)) {
                paramMap.put(paramName, "%" + value + "%");
            } else {
                paramMap.put(paramName, value);
            }
            whereParts.add(columnExpr.apply(field) + " " + sqlOp + " #{paramMap." + paramName + "}");
        }
    }

    /**
     * 把 AND/OR 树翻译成 SQL 条件串。
     * <p>
     * 值一律走 {@code #{paramMap.x}} 绑定，树里能进 SQL 的只有列名/聚合表达式与括号、
     * 逻辑关键字——结构与 {@link ComputeFilterTree} 的内存求值完全同构，所以同一棵树
     * 在两条路径上成立与否必然一致。
     */
    private void appendFilterTree(List<String> parts, Map<String, Object> paramMap, int[] paramIndex,
                                  JSONObject tree, java.util.function.Function<String, String> exprOf, String source) {
        if (tree == null) {
            return;
        }
        parts.add(filterTreeSql(tree, paramMap, paramIndex, exprOf, source, 1));
    }

    private String filterTreeSql(JSONObject node, Map<String, Object> paramMap, int[] paramIndex,
                                 java.util.function.Function<String, String> exprOf, String source, int depth) {
        if (depth > ComputeFilterTree.MAX_DEPTH) {
            throw new ServiceException(source + "嵌套超过 " + ComputeFilterTree.MAX_DEPTH + " 层上限");
        }
        if (!ComputeFilterTree.isGroup(node)) {
            return filterLeafSql(node, paramMap, paramIndex, exprOf, source);
        }
        String joiner = "or".equalsIgnoreCase(node.getString("op")) ? " OR " : " AND ";
        JSONArray when = node.getJSONArray("when");
        if (when == null || when.isEmpty()) {
            throw new ServiceException(source + "条件组里没有任何条件");
        }
        List<String> children = new ArrayList<>();
        for (Object item : when) {
            JSONObject child = item instanceof JSONObject object
                    ? object
                    : (item instanceof java.util.Map<?, ?> map ? JSONObject.from(map) : null);
            if (child == null) {
                throw new ServiceException(source + "条件项必须是对象");
            }
            children.add(filterTreeSql(child, paramMap, paramIndex, exprOf, source, depth + 1));
        }
        return "(" + String.join(joiner, children) + ")";
    }

    private String filterLeafSql(JSONObject leaf, Map<String, Object> paramMap, int[] paramIndex,
                                 java.util.function.Function<String, String> exprOf, String source) {
        String field = leaf.getString("field");
        if (StringUtils.isBlank(field)) {
            throw new ServiceException(source + "缺少字段");
        }
        String op = ComputeFilterTree.leafOp(leaf);
        String sqlOp = FILTER_OPS.get(op);
        if (sqlOp == null) {
            throw new ServiceException("不支持的过滤操作符：" + op);
        }
        String expr = exprOf.apply(field);
        if (StringUtils.isBlank(expr)) {
            throw new ServiceException(source + "字段 " + field + " 无法解析为查询表达式");
        }
        Object value = leaf.get("value");
        if (value == null) {
            throw new ServiceException(source + "字段 " + field + " 缺少比较值");
        }
        String paramName = "p" + paramIndex[0]++;
        paramMap.put(paramName, "LIKE".equals(sqlOp) ? "%" + value + "%" : value);
        return expr + " " + sqlOp + " #{paramMap." + paramName + "}";
    }

    /**
     * 度量别名 → 聚合函数，供归并「其他」时判断能否安全合并。
     * 别名集合与 config.measures 顺序一致（都是 value/m1/m2...），所以按下标取 agg 即可；
     * 取不到时按 SUM 处理是危险的（AVG 会被当成可加），这里宁可报错。
     */
    private Map<String, String> aliasAggByMeasure(Map<String, String> exprByAlias, List<String> measureAliases,
                                                  JSONObject config) {
        Map<String, String> aliasAgg = new LinkedHashMap<>();
        JSONArray measures = config.getJSONArray("measures");
        for (int i = 0; i < measureAliases.size(); i++) {
            String alias = measureAliases.get(i);
            String agg = "SUM";
            if (measures != null && i < measures.size()) {
                JSONObject measure = measures.getJSONObject(i);
                agg = StringUtils.isNotBlank(measure == null ? null : measure.getString("agg"))
                        ? measure.getString("agg").toUpperCase() : "SUM";
            }
            aliasAgg.put(alias, agg);
        }
        return aliasAgg;
    }

    /** HAVING 里引用的是聚合别名，翻译成原表达式；维度别名同样可用（按分组列筛） */
    private String havingExprByAlias(String alias, Map<String, String> exprByAlias) {
        String expr = exprByAlias.get(alias);
        if (expr == null) {
            throw new ServiceException("聚合过滤引用了不存在的字段：" + alias);
        }
        return expr;
    }

    /** 过滤叶子传进来的是字段名字符串，这里补一个与 JSONObject 版同校验的重载 */
    private String requireColumn(String field, Map<String, String> columnTypes, String source) {
        String checked = requireIdentifier(field, source + "字段");
        if (columnTypes.get(checked) == null) {
            throw new ServiceException(source + "字段 " + checked + " 不在数据集列快照中，请刷新数据集");
        }
        return checked;
    }

    /**
     * 列名双重校验：标识符白名单 + 必须存在于数据集列快照
     */
    private String requireColumn(JSONObject node, Map<String, String> columnTypes, String source) {
        String field = node.getString("field");
        String dataType = columnTypes.get(requireIdentifier(field, source + "字段"));
        if (dataType == null) {
            throw new ServiceException(source + "字段 " + field + " 不在数据集列快照中，请刷新数据集");
        }
        return field;
    }

    private String requireIdentifier(String name, String what) {
        if (StringUtils.isBlank(name) || !IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new ServiceException("非法标识符：" + what + "「" + name + "」");
        }
        return name;
    }

    private String decorateAndValidate(String logicalTableCode) {
        // 逻辑编码 → 物理表名：与数据集保存同一条三级容错解析（带前缀/原编码/组织内后缀），
        // 保证数据集保存与运行时聚合看到同一张表；解析失败给出可读错误
        String physical = tableResolver.resolvePhysicalTableCode(logicalTableCode);
        if (physical == null) {
            throw new ServiceException("数据集绑定的动态表不存在：" + logicalTableCode);
        }
        return physical;
    }

    private String normalizeGranularity(String granularity) {
        String gran = granularity.trim().toUpperCase();
        if (!GRANULARITIES.contains(gran)) {
            throw new ServiceException("不支持的时间粒度：" + granularity + "（" + ComputeSpecs.granularityHint() + "）");
        }
        return gran;
    }

    /**
     * 列快照里的中文名：fieldCode → fieldName。只用于回传给前端做图例与列头，
     * 不参与 SQL 生成；缺失时前端回退字段编码。
     */
    private Map<String, String> parseColumnNames(com.privernal.dynamic.report.domain.DynamicDataset dataset) {
        Map<String, String> names = new LinkedHashMap<>();
        JSONObject snapshot = dataset.getColumnSnapshot();
        JSONArray columns = snapshot == null ? null : snapshot.getJSONArray("columns");
        if (columns == null) {
            return names;
        }
        for (int i = 0; i < columns.size(); i++) {
            JSONObject column = columns.getJSONObject(i);
            String fieldCode = column.getString("fieldCode");
            String fieldName = column.getString("fieldName");
            if (StringUtils.isNotBlank(fieldCode) && StringUtils.isNotBlank(fieldName)) {
                names.put(fieldCode, fieldName);
            }
        }
        return names;
    }

    /**
     * 列快照解析：fieldCode → dataType（number|date|text）。
     * 快照由数据集服务在建数据集时从 dynamic_form_column 解析落库，这里只读取校验。
     */
    private Map<String, String> parseColumnTypes(com.privernal.dynamic.report.domain.DynamicDataset dataset) {
        Map<String, String> columnTypes = new LinkedHashMap<>();
        JSONObject snapshot = dataset.getColumnSnapshot();
        if (snapshot == null) {
            throw new ServiceException("数据集列快照为空，请刷新数据集：" + dataset.getDatasetCode());
        }
        JSONArray columns = snapshot.getJSONArray("columns");
        if (columns == null || columns.isEmpty()) {
            throw new ServiceException("数据集列快照为空，请刷新数据集：" + dataset.getDatasetCode());
        }
        for (int i = 0; i < columns.size(); i++) {
            JSONObject column = columns.getJSONObject(i);
            String fieldCode = column.getString("fieldCode");
            if (StringUtils.isNotBlank(fieldCode)) {
                columnTypes.put(fieldCode, StringUtils.isNotBlank(column.getString("dataType"))
                        ? column.getString("dataType") : "text");
            }
        }
        return columnTypes;
    }
}
