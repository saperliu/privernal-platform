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

package com.privernal.dynamic.datav.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多源数据集的内存聚合引擎（M3）。
 * <p>
 * HTTP/Excel/WS 这类「拉到的是行」的源没有 SQL 可下推，维度/度量/过滤/排序/限流在这里做。
 * 输出与 {@code DynamicReportQueryService} 的 SQL 路径完全同形：
 * {reportType, rows:[{dim0,value,m1...}], dimensionAliases/Labels, measureAliases/Labels, limit}——
 * 前端投影层与图表组件因此对源类型无感。
 * <p>
 * 纯内存实现有明确的量上限（{@code MAX_ROWS}），超限由连接器截断，这里不再二次放大。
 *
 * @author saperliu
 */
@Service
public class DatavRowAggregator {

    private static final int MAX_MEASURES = 5;

    private static final Set<String> AGG_FUNCS = ComputeSpecs.AGG_FUNCS;

    private static final Set<String> FILTER_OPS = ComputeSpecs.FILTER_OPS;

    private static final Set<String> GRANULARITIES = ComputeSpecs.GRANULARITIES;

    /**
     * @param rows           连接器产出的原始行（键=fieldCode）
     * @param columnTypes    列快照的 fieldCode→dataType
     * @param config         图表配置 {reportType, dimensions, measures, sort, limit, filters, filterTree, having}
     * @param runtimeFilters 运行时过滤（大屏参数），可为空
     */
    public Map<String, Object> aggregate(List<Map<String, Object>> rows,
                                         Map<String, String> columnTypes,
                                         Map<String, String> columnNames,
                                         JSONObject config,
                                         JSONArray runtimeFilters) {
        if (config == null) {
            config = new JSONObject();
        }
        String reportType = StringUtils.isNotBlank(config.getString("reportType"))
                ? config.getString("reportType") : "bar";
        JSONArray dimensions = nonNull(config.getJSONArray("dimensions"));
        JSONArray measures = nonNull(config.getJSONArray("measures"));

        if (measures.size() > MAX_MEASURES) {
            throw new ServiceException("单个图表最多 " + MAX_MEASURES + " 个度量");
        }

        // —— 过滤（数据集默认 + 图表 + 运行时）——
        List<Map<String, Object>> filtered = new ArrayList<>(rows);
        // 默认过滤、条件树、运行时参数三者是 AND 关系，合成一颗树后整树求值
        applyFilterTree(filtered, ComputeFilterTree.normalize(
                ComputeFilterTree.normalize(config.getJSONObject("filterTree"), config.getJSONArray("filters")),
                runtimeFilters));

        // —— 维度键 + 度量声明 ——
        List<DimSpec> dims = new ArrayList<>();
        for (int i = 0; i < dimensions.size(); i++) {
            JSONObject dim = dimensions.getJSONObject(i);
            String field = requireColumn(dim.getString("field"), columnTypes, "维度");
            String granularity = normalizeGranularity(dim.getString("granularity"), columnTypes.get(field));
            dims.add(new DimSpec(field, granularity, ComputeBucket.parse(dim), "dim" + i,
                    columnNames.getOrDefault(field, field)));
        }
        boolean singleValue = "stat".equals(reportType) || "gauge".equals(reportType);
        if (dims.isEmpty() && !singleValue) {
            throw new ServiceException("该图表类型至少需要一个维度字段");
        }

        List<MeasureSpec> msrs = new ArrayList<>();
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
            String label;
            if ("COUNT".equals(agg) && StringUtils.isBlank(field)) {
                label = "计数";
            } else if ("COUNTD".equals(agg)) {
                String countField = requireColumn(field, columnTypes, "度量");
                // 去重计数对文本列同样有意义（有多少个不同分类），所以不走数值校验——与 SQL 侧同一决定
                label = columnNames.getOrDefault(countField, countField);
            } else {
                String dataType = columnTypes.get(requireColumn(field, columnTypes, "度量"));
                if (!"number".equals(dataType)) {
                    throw new ServiceException("度量字段 " + field + " 不是数值列，无法执行 " + agg);
                }
                label = columnNames.getOrDefault(field, field);
            }
            msrs.add(new MeasureSpec(agg, field, i == 0 ? "value" : "m" + i, label));
        }
        if (msrs.isEmpty()) {
            msrs.add(new MeasureSpec("COUNT", null, "value", "计数"));
        }

        // —— 分组聚合：桶里先放 Accum（含计数，AVG 靠它），出桶时 finalize 成数值 ——
        Map<String, JSONObject> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : filtered) {
            StringBuilder keyBuilder = new StringBuilder();
            JSONObject out = new JSONObject();
            for (DimSpec dim : dims) {
                String cell = dimValue(row.get(dim.field), dim.granularity);
                if (dim.bucket != null) {
                    // 分桶在时间粒度之后：两者对同一列同时生效时，先按粒度折再落档更符合直觉
                    String binned = ComputeBucket.labelOf(row.get(dim.field), dim.bucket);
                    if (binned != null) {
                        cell = binned;
                    }
                }
                out.put(dim.alias, cell);
                keyBuilder.append(cell).append('\u0000');
            }
            String key = keyBuilder.toString();
            JSONObject bucket = groups.get(key);
            if (bucket == null) {
                for (MeasureSpec spec : msrs) {
                    out.put(spec.alias, newAccumulator(spec, row));
                }
                groups.put(key, out);
            } else {
                for (MeasureSpec spec : msrs) {
                    bucket.put(spec.alias, fold(spec, row, bucket.get(spec.alias)));
                }
            }
        }

        List<JSONObject> result = new ArrayList<>(groups.values());
        if (dims.isEmpty()) {
            // stat/gauge：把唯一的聚合桶直接吐成一行
            JSONObject single = new JSONObject();
            Accum only = result.isEmpty() ? null : accumOf(result.get(0), msrs.get(0));
            single.put(msrs.get(0).alias, only == null ? 0L : only.value(msrs.get(0).agg));
            result = new ArrayList<>();
            result.add(single);
        } else {
            for (JSONObject row : result) {
                BigDecimal groupWeight = null;
                for (MeasureSpec spec : msrs) {
                    Accum accum = accumOf(row, spec);
                    row.put(spec.alias, accum == null ? 0L : accum.value(spec.agg));
                    if (accum != null && groupWeight == null) {
                        groupWeight = BigDecimal.valueOf(accum.count);
                    }
                }
                // 组内行数：只有配了「其他」归并才会用到，返回前统一剥掉
                row.put(ComputeTopN.WEIGHT_ALIAS, groupWeight == null ? 0L : groupWeight);
            }
        }

        // —— 聚合后过滤（HAVING 语义）：必须在 finalize 之后，字段是聚合别名 ——
        applyFilterTree(result, ComputeFilterTree.normalize(
                config.getJSONObject("having"), config.getJSONArray("havingFilters")));

        // —— TopN 与「其他」归并：与 SQL 路径共用同一段后处理 ——
        Map<String, String> aliasAgg = new java.util.LinkedHashMap<>();
        for (MeasureSpec spec : msrs) {
            aliasAgg.put(spec.alias, spec.agg);
        }
        ComputeTopN.apply(result, config.getJSONObject("topN"), aliasAgg,
                dims.isEmpty() ? msrs.get(0).alias : dims.get(0).alias);
        result.forEach(row -> row.remove(ComputeTopN.WEIGHT_ALIAS));

        // —— 排序与限流 ——
        sortRows(result, config, dims, msrs);
        int limit = Math.max(1, Math.min(1000, config.getIntValue("limit", 50)));
        if (result.size() > limit) {
            result = new ArrayList<>(result.subList(0, limit));
        }

        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("reportType", reportType);
        outcome.put("rows", result);
        outcome.put("limit", limit);
        List<String> dimAliases = new ArrayList<>();
        List<String> dimLabels = new ArrayList<>();
        for (DimSpec dim : dims) {
            dimAliases.add(dim.alias);
            dimLabels.add(dim.label);
        }
        List<String> msrAliases = new ArrayList<>();
        List<String> msrLabels = new ArrayList<>();
        for (MeasureSpec spec : msrs) {
            msrAliases.add(spec.alias);
            msrLabels.add(spec.label);
        }
        outcome.put("dimensionAliases", dimAliases);
        outcome.put("dimensionLabels", dimLabels);
        outcome.put("measureAliases", msrAliases);
        outcome.put("measureLabels", msrLabels);
        return outcome;
    }

    // ---------------- 过滤 ----------------

    /** 行集合既可能是原始行（Map）也可能是聚合后的结果行（JSONObject），所以签名放宽到 extends */
    private void applyFilterTree(List<? extends Map<String, Object>> rows, JSONObject tree) {
        if (tree == null) {
            return;
        }
        rows.removeIf(row -> !ComputeFilterTree.evaluate(tree, leaf -> matchesLeaf(leaf, row)));
    }

    private boolean matchesLeaf(JSONObject leaf, Map<String, Object> row) {
        String field = leaf.getString("field");
        String op = ComputeFilterTree.leafOp(leaf);
        if (!FILTER_OPS.contains(op)) {
            throw new ServiceException("不支持的操作符：" + op);
        }
        return matches(row.get(field), op, leaf.get("value"));
    }

    static boolean matches(Object actual, String op, Object expected) {
        if ("ne".equals(op)) {
            return !matches(actual, "eq", expected);
        }
        if ("like".equals(op)) {
            String a = actual == null ? "" : String.valueOf(actual);
            String e = expected == null ? "" : String.valueOf(expected);
            return a.contains(e.replace("%", ""));
        }
        if ("gt".equals(op) || "ge".equals(op) || "lt".equals(op) || "le".equals(op)) {
            BigDecimal left = toDecimal(actual);
            BigDecimal right = toDecimal(expected);
            if (left == null || right == null) {
                return false;
            }
            int cmp = left.compareTo(right);
            return "gt".equals(op) ? cmp > 0 : "ge".equals(op) ? cmp >= 0 : "lt".equals(op) ? cmp < 0 : cmp <= 0;
        }
        // eq：数值列按数值比（后端度量常以字符串回传）
        BigDecimal left = toDecimal(actual);
        BigDecimal right = toDecimal(expected);
        if (left != null && right != null) {
            return left.compareTo(right) == 0;
        }
        return actual == null ? expected == null : String.valueOf(actual).equals(expected == null ? null : String.valueOf(expected));
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        if (value instanceof String) {
            try {
                return new BigDecimal(((String) value).trim());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    // ---------------- 维度 ----------------

    static String dimValue(Object raw, String granularity) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (StringUtils.isBlank(granularity) || text.isEmpty()) {
            return text;
        }
        LocalDate date = parseDate(text);
        if (date == null) {
            return text;
        }
        switch (granularity) {
            case "YEAR":
                return String.valueOf(date.getYear());
            case "MONTH":
                return String.format("%d-%02d", date.getYear(), date.getMonthValue());
            case "WEEK":
                LocalDate weekStart = date.minusDays(date.getDayOfWeek().getValue() - 1L);
                return weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "HOUR":
                // 输出必须与 SQL 路径逐字一致（MySQL %Y-%m-%d %H:00、PG YYYY-MM-DD HH24）：
                // 同一份数据在动态表上是 09:00、在外部库上是 9:00，跨源的图就没法对得上
                return date + " " + String.format("%02d:00", hourOf(text));
            case "MINUTE":
                int[] hm = hourMinuteOf(text);
                return date + " " + String.format("%02d:%02d", hm[0], hm[1]);
            default:
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }


    /** 从时间文本里取小时；只有日期没有时间时按 0 点算（DATE 列按小时分组就该全天一组） */
    static int hourOf(String text) {
        return hourMinuteOf(text)[0];
    }

    /** 取 [时, 分]。接受 "yyyy-MM-dd HH:mm:ss"、"... HH:mm"、"...THH:mm"，取不到时返回 0,0 */
    static int[] hourMinuteOf(String text) {
        String trimmed = text.trim();
        int index = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch == 'T' || ch == 't') {
                index = i + 1;
                break;
            }
            if (ch == ' ') {
                index = i + 1;
            }
        }
        if (index < 0 || index + 1 >= trimmed.length()) {
            return new int[]{0, 0};
        }
        String rest = trimmed.substring(index);
        String[] parts = rest.split(":");
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return new int[]{0, 0};
            }
            return new int[]{hour, minute};
        } catch (NumberFormatException e) {
            return new int[]{0, 0};
        }
    }

    private static LocalDate parseDate(String text) {
        String head = text.length() >= 10 ? text.substring(0, 10) : text;
        for (String pattern : new String[]{"yyyy-MM-dd", "yyyy/MM/dd"}) {
            try {
                return LocalDate.parse(head, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
                // 尝试下一格式
            }
        }
        return null;
    }

    private String normalizeGranularity(String raw, String dataType) {
        if (!"date".equals(dataType)) {
            return "";
        }
        String text = String.valueOf(raw == null ? "" : raw).toUpperCase();
        return GRANULARITIES.contains(text) ? text : "";
    }

    // ---------------- 聚合 ----------------

    /** 聚合态：SUM/MAX/MIN 只用 sum；COUNT 只用 count；AVG 两者都用；COUNTD 用 distinct */
    private static final class Accum {
        BigDecimal sum;
        long count;
        java.util.Set<String> distinct;

        Object value(String agg) {
            if ("COUNT".equals(agg)) {
                return count;
            }
            if ("COUNTD".equals(agg)) {
                return distinct == null ? 0L : (long) distinct.size();
            }
            if (sum == null) {
                return 0;
            }
            if ("AVG".equals(agg)) {
                return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
            }
            return sum;
        }
    }

    private Object newAccumulator(MeasureSpec spec, Map<String, Object> row) {
        Accum accum = new Accum();
        foldInto(accum, spec, row);
        return accum;
    }

    private Object fold(MeasureSpec spec, Map<String, Object> row, Object acc) {
        Accum accum = acc instanceof Accum ? (Accum) acc : new Accum();
        foldInto(accum, spec, row);
        return accum;
    }

    private void foldInto(Accum accum, MeasureSpec spec, Map<String, Object> row) {
        accum.count += 1;
        if ("COUNTD".equals(spec.agg)) {
            String key = distinctKey(row.get(spec.field));
            if (key != null) {
                if (accum.distinct == null) {
                    accum.distinct = new java.util.HashSet<>();
                }
                accum.distinct.add(key);
            }
            return;
        }
        if ("COUNT".equals(spec.agg)) {
            return;
        }
        BigDecimal value = toDecimal(row.get(spec.field));
        if (value == null) {
            return;
        }
        if (accum.sum == null || "MAX".equals(spec.agg) && value.compareTo(accum.sum) > 0
                || "MIN".equals(spec.agg) && value.compareTo(accum.sum) < 0
                || "SUM".equals(spec.agg) || "AVG".equals(spec.agg)) {
            if (accum.sum == null || "SUM".equals(spec.agg) || "AVG".equals(spec.agg)) {
                accum.sum = accum.sum == null ? value : accum.sum.add(value);
            } else {
                accum.sum = value;
            }
        }
    }

    /**
     * 去重键。null 返回 null 表示不计入——对齐 SQL 的 `COUNT(DISTINCT x)`（它同样忽略 NULL）。
     * <p>
     * 数值必须归一后再比：SQL 侧 1.0 与 1.00 是同一个值，而内存里一个是 BigDecimal("1.0")、
     * 一个是外部源带过来的字符串 "1.00"，不归一就会多算一个，两条路径给出不同的去重数。
     * 文本一律按原样比（不 trim、不转大小写），因为 SQL 的 DISTINCT 也是这样。
     */
    static String distinctKey(Object raw) {
        if (raw == null) {
            return null;
        }
        BigDecimal decimal = toDecimal(raw);
        if (decimal != null) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(raw);
    }

    private Accum accumOf(JSONObject row, MeasureSpec spec) {
        Object acc = row.get(spec.alias);
        return acc instanceof Accum ? (Accum) acc : null;
    }

    // ---------------- 排序 ----------------

    private void sortRows(List<JSONObject> rows, JSONObject config, List<DimSpec> dims, List<MeasureSpec> msrs) {
        JSONObject sort = config.getJSONObject("sort");
        if (sort == null || StringUtils.isBlank(sort.getString("field"))) {
            return;
        }
        String field = sort.getString("field");
        String direction = StringUtils.defaultIfBlank(sort.getString("order"), "asc").toLowerCase();
        List<String> aliases = new ArrayList<>();
        for (DimSpec dim : dims) {
            aliases.add(dim.alias);
        }
        for (MeasureSpec spec : msrs) {
            aliases.add(spec.alias);
        }
        if (!aliases.contains(field)) {
            throw new ServiceException("排序字段必须是维度或度量：" + field);
        }
        Comparator<JSONObject> comparator = Comparator.comparing(
                row -> toDecimal(row.get(field)) != null ? toDecimal(row.get(field))
                        : BigDecimal.valueOf(Long.MAX_VALUE),
                Comparator.nullsLast(Comparator.naturalOrder()));
        // 文本维度没有数值形态，按字符串比较
        boolean numeric = rows.stream().anyMatch(row -> toDecimal(row.get(field)) != null);
        if (!numeric) {
            comparator = Comparator.comparing(row -> String.valueOf(row.get(field)));
        }
        if ("desc".equals(direction)) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator);
    }

    private static JSONArray nonNull(JSONArray array) {
        return array == null ? new JSONArray() : array;
    }

    private String requireColumn(String field, Map<String, String> columnTypes, String role) {
        if (StringUtils.isBlank(field)) {
            throw new ServiceException(role + "字段不能为空");
        }
        if (!columnTypes.containsKey(field)) {
            throw new ServiceException(role + "字段不在数据集列快照中：" + field);
        }
        return field;
    }

    private static final class DimSpec {
        final String field;
        final String granularity;

        /** 数值分桶定义；null 表示不分桶 */
        final com.alibaba.fastjson2.JSONObject bucket;
        final String alias;
        final String label;

        DimSpec(String field, String granularity, com.alibaba.fastjson2.JSONObject bucket, String alias, String label) {
            this.field = field;
            this.granularity = granularity;
            this.bucket = bucket;
            this.alias = alias;
            this.label = label;
        }
    }

    private static final class MeasureSpec {
        final String agg;
        final String field;
        final String alias;
        final String label;

        MeasureSpec(String agg, String field, String alias, String label) {
            this.agg = agg;
            this.field = field;
            this.alias = alias;
            this.label = label;
        }
    }
}
