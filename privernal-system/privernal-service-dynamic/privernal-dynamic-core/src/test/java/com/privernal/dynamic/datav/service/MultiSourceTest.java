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

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 多源接入的纯函数单测：SSRF 拦截、HTTP 行规范化/类型推断、内存聚合。
 * 不起 Spring、不发真实网络请求。
 *
 * @author saperliu
 */
class MultiSourceTest {

    private final DatavRowAggregator aggregator = new DatavRowAggregator();

    // ---------------- SSRF ----------------

    @Test
    void blocksLoopbackAndPrivateAddresses() throws Exception {
        assertThat(com.privernal.dynamic.datav.service.connector.HttpSourceConnector.isBlocked(
                InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(com.privernal.dynamic.datav.service.connector.HttpSourceConnector.isBlocked(
                InetAddress.getByName("10.1.2.3"))).isTrue();
        assertThat(com.privernal.dynamic.datav.service.connector.HttpSourceConnector.isBlocked(
                InetAddress.getByName("192.168.1.1"))).isTrue();
        assertThat(com.privernal.dynamic.datav.service.connector.HttpSourceConnector.isBlocked(
                InetAddress.getByName("172.16.0.1"))).isTrue();
        assertThat(com.privernal.dynamic.datav.service.connector.HttpSourceConnector.isBlocked(
                InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(com.privernal.dynamic.datav.service.connector.HttpSourceConnector.isBlocked(
                InetAddress.getByName("100.64.0.1"))).isTrue();
        assertThat(com.privernal.dynamic.datav.service.connector.HttpSourceConnector.isBlocked(
                InetAddress.getByName("8.8.8.8"))).isFalse();
    }

    // ---------------- 行规范化与类型推断 ----------------

    @Test
    void flattensNestedObjectsAndStringifiesArrays() {
        JSONArray array = new JSONArray();
        JSONObject row = new JSONObject();
        row.put("name", "数学");
        row.put("count", 12);
        JSONObject nested = new JSONObject();
        nested.put("level", "A");
        row.put("extra", nested);
        row.put("tags", JSONArray.of("x", "y"));
        array.add(row);

        List<Map<String, Object>> rows = com.privernal.dynamic.datav.service.connector.HttpSourceConnector.normalizeRows(array, 100);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("name", "数学");
        assertThat(rows.get(0)).containsEntry("extra.level", "A");
        assertThat(String.valueOf(rows.get(0).get("tags"))).contains("x");
    }

    @Test
    void truncatesToMaxRows() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < 10; i++) {
            array.add(new JSONObject(Map.of("v", i)));
        }
        List<Map<String, Object>> rows = com.privernal.dynamic.datav.service.connector.HttpSourceConnector.normalizeRows(array, 3);
        assertThat(rows).hasSize(3);
    }

    @Test
    void infersColumnTypesFromSample() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("name", "数学", "count", "3", "day", "2026-10-01"));
        rows.add(rowOf("name", "英语", "count", 5, "day", "2026-10-02 08:00:00"));
        JSONObject snapshot = com.privernal.dynamic.datav.service.connector.HttpSourceConnector.inferColumns(rows);
        Map<String, String> types = new HashMap<>();
        snapshot.getJSONArray("columns").forEach(item -> {
            JSONObject column = (JSONObject) item;
            types.put(column.getString("fieldCode"), column.getString("dataType"));
        });
        assertThat(types.get("name")).isEqualTo("text");
        assertThat(types.get("count")).isEqualTo("number");
        assertThat(types.get("day")).isEqualTo("date");
    }

    // ---------------- 内存聚合 ----------------

    @Test
    void aggregatesSumCountAvgWithGrouping() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("city", "北京", "amount", "10"));
        rows.add(rowOf("city", "北京", "amount", "20"));
        rows.add(rowOf("city", "上海", "amount", 5));

        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("city", "text", "amount", "number"),
                Map.of("city", "城市", "amount", "金额"),
                config("[{field:'city'}]", "[{field:'amount',agg:'SUM'},{field:'amount',agg:'AVG'},{field:'',agg:'COUNT'}]"),
                null);

        assertThat(result.get("dimensionAliases")).isEqualTo(List.of("dim0"));
        assertThat(result.get("measureAliases")).isEqualTo(List.of("value", "m1", "m2"));
        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        assertThat(out).hasSize(2);
        JSONObject first = out.get(0);
        assertThat(first.getString("dim0")).isEqualTo("北京");
        assertThat(first.getBigDecimal("value")).isEqualByComparingTo("30");
        assertThat(first.getBigDecimal("m1")).isEqualByComparingTo("15");
        assertThat(first.getLongValue("m2")).isEqualTo(2);
    }

    @Test
    void appliesRuntimeFiltersBeforeAggregation() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("city", "北京", "amount", 10));
        rows.add(rowOf("city", "上海", "amount", 5));

        JSONArray runtime = JSONArray.of(new JSONObject(Map.of("field", "city", "op", "eq", "value", "北京")));
        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("city", "text", "amount", "number"), Map.of(),
                config("[{field:'city'}]", "[{field:'amount',agg:'SUM'}]"), runtime);

        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getString("dim0")).isEqualTo("北京");
    }

    @Test
    void rejectsMeasureOnNonNumberColumn() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("city", "北京", "amount", 1));
        assertThatThrownBy(() -> aggregator.aggregate(rows,
                Map.of("city", "text"), Map.of(),
                config("[{field:'city'}]", "[{field:'city',agg:'SUM'}]"), null))
                .hasMessageContaining("不是数值列");
    }

    @Test
    void groupsDateColumnByMonthGranularity() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("day", "2026-09-01", "amount", 1));
        rows.add(rowOf("day", "2026-09-20", "amount", 2));
        rows.add(rowOf("day", "2026-10-05", "amount", 4));

        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("day", "date", "amount", "number"), Map.of(),
                config("[{field:'day',granularity:'MONTH'}]", "[{field:'amount',agg:'SUM'}]"), null);

        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getString("dim0")).isEqualTo("2026-09");
        assertThat(out.get(0).getBigDecimal("value")).isEqualByComparingTo("3");
    }

    @Test
    void sortsDescByMeasureAndLimits() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            rows.add(rowOf("name", "n" + i, "amount", i));
        }
        JSONObject config = config("[{field:'name'}]", "[{field:'amount',agg:'SUM'}]");
        config.put("sort", new JSONObject(Map.of("field", "value", "order", "desc")));
        config.put("limit", 2);

        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("name", "text", "amount", "number"), Map.of(), config, null);
        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getString("dim0")).isEqualTo("n5");
        assertThat(result.get("limit")).isEqualTo(2);
    }

    private static Map<String, Object> rowOf(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            row.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return row;
    }

    private static JSONObject config(String dimensions, String measures) {
        JSONObject config = new JSONObject();
        config.put("dimensions", JSONArray.parse(dimensions.replace('\'', '"')));
        config.put("measures", JSONArray.parse(measures.replace('\'', '"')));
        return config;
    }

    // ---------------- M4.1 计算层：COUNTD 与亚天粒度 ----------------

    @Test
    void countsDistinctValuesIgnoringNullAndNormalisingNumbers() {
        List<Map<String, Object>> rows = new ArrayList<>();
        // 同一个值四种写法：BigDecimal(1.0)、字符串 "1.00"、整数 1 —— SQL 的 DISTINCT 按数值比，
        // 内存侧不归一就会多算，两条路径给出去重数就不同
        rows.add(rowOf("city", "北京", "amount", new java.math.BigDecimal("1.0")));
        rows.add(rowOf("city", "北京", "amount", "1.00"));
        rows.add(rowOf("city", "北京", "amount", 1));
        rows.add(rowOf("city", "北京", "amount", null));
        rows.add(rowOf("city", "北京", "amount", 2));

        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("city", "text", "amount", "number"), Map.of(),
                config("[{field:'city'}]", "[{field:'amount',agg:'COUNTD'}]"), null);

        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getLongValue("value")).isEqualTo(2);
    }

    @Test
    void countsDistinctTextValuesWithoutNumericCheck() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("k", 1, "city", "北京"));
        rows.add(rowOf("k", 1, "city", "上海"));
        rows.add(rowOf("k", 1, "city", "北京"));

        // 去重计数对文本列有意义（有多少个不同城市），不能被「度量必须是数值列」挡掉
        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("k", "number", "city", "text"), Map.of(),
                config("[{field:'k'}]", "[{field:'city',agg:'COUNTD'}]"), null);

        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getLongValue("value")).isEqualTo(2);
    }

    @Test
    void rejectsMedianOnBothPathsBecauseOnlyOneCanComputeItExactly() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("amount", 1));
        // MEDIAN 在 MySQL 没有原生函数、只能靠窗口函数凑，而内存侧能给精确值。
        // 一条路径近似、一条精确比两边都直接拒绝危险，所以它刻意不在白名单里。
        assertThatThrownBy(() -> aggregator.aggregate(rows, Map.of("amount", "number"), Map.of(),
                config("[{field:'amount'}]", "[{field:'amount',agg:'MEDIAN'}]"), null))
                .isInstanceOf(com.privernal.common.core.exception.ServiceException.class)
                .hasMessageContaining("不支持的聚合函数")
                .hasMessageContaining("COUNTD");
    }

    @Test
    void groupsByHourWithoutMergingTheSameHourAcrossDays() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("ts", "2026-01-05 09:00:00", "amount", 1));
        rows.add(rowOf("ts", "2026-01-06 09:30:00", "amount", 2));
        rows.add(rowOf("ts", "2026-01-06 10:00:00", "amount", 4));

        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("ts", "date", "amount", "number"), Map.of(),
                config("[{field:'ts',granularity:'HOUR'}]", "[{field:'amount',agg:'SUM'}]"), null);

        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        // 两天里的 9 点必须是两组：分组标签带日期部分，与 SQL 侧 %Y-%m-%d %H:00 逐字一致
        assertThat(out).extracting(row -> row.getString("dim0"))
                .containsExactly("2026-01-05 09:00", "2026-01-06 09:00", "2026-01-06 10:00");
        assertThat(out).extracting(row -> row.getBigDecimal("value").toString())
                .containsExactly("1", "2", "4");
    }

    @Test
    void groupsByMinuteAndKeepsDateOnlyColumnsInOneBucket() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("ts", "2026-01-05 09:05:12", "amount", 1));
        rows.add(rowOf("ts", "2026-01-05 09:05:59", "amount", 2));
        // 只有日期、没有时间：按分钟分组就该全天一组，而不是解析失败丢行
        rows.add(rowOf("ts", "2026-01-06", "amount", 5));

        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("ts", "date", "amount", "number"), Map.of(),
                config("[{field:'ts',granularity:'MINUTE'}]", "[{field:'amount',agg:'SUM'}]"), null);

        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        assertThat(out).extracting(row -> row.getString("dim0"))
                .containsExactly("2026-01-05 09:05", "2026-01-06 00:00");
        assertThat(out).extracting(row -> row.getBigDecimal("value").toString())
                .containsExactly("3", "5");
    }

    /**
     * 两条路径共用一份白名单是这次改动真正想钉住的东西：
     * 加 HOUR/MINUTE 时我只改了内存侧那一处常量，SQL 侧漏改，于是同一粒度在动态表上报「不支持」、
     * 在外部库上正常出图。现在两侧都引用 ComputeSpecs，且这里断言它们确实是同一个集合。
     */
    @Test
    void bothComputePathsShareOneWhitelist() {
        assertThat(com.privernal.dynamic.report.service.DynamicReportQueryService.AGG_FUNCS)
                .isSameAs(ComputeSpecs.AGG_FUNCS);
        assertThat(ComputeSpecs.AGG_FUNCS).contains("COUNTD", "SUM", "COUNT", "AVG", "MAX", "MIN")
                .doesNotContain("MEDIAN", "PERCENTILE");
        assertThat(ComputeSpecs.GRANULARITIES).contains("MINUTE", "HOUR", "DAY", "WEEK", "MONTH", "YEAR");
        assertThat(ComputeSpecs.FILTER_OPS).contains("eq", "ne", "like", "gt", "ge", "lt", "le");
    }

    // ---------------- M4.3 过滤条件树与聚合后过滤 ----------------

    private static JSONObject orTree(JSONObject... leaves) {
        JSONObject group = new JSONObject();
        group.put("op", "or");
        group.put("when", JSONArray.of((Object[]) leaves));
        return group;
    }

    private static JSONObject leaf(String field, String op, Object value) {
        JSONObject node = new JSONObject();
        node.put("field", field);
        node.put("op", op);
        node.put("value", value);
        return node;
    }

    @Test
    void appliesOrTreeThatFlatAndCannotExpress() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("city", "北京", "amount", 10));
        rows.add(rowOf("city", "上海", "amount", 20));
        rows.add(rowOf("city", "广州", "amount", 30));

        JSONObject config = config("[{field:'city'}]", "[{field:'amount',agg:'SUM'}]");
        config.put("filterTree", orTree(leaf("city", "eq", "北京"), leaf("city", "eq", "广州")));

        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("city", "text", "amount", "number"), Map.of(), config, null);

        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        // 平铺数组只能表达 AND，这条断言证明 OR 分支真的生效了
        assertThat(out).extracting(row -> row.getString("dim0")).containsExactly("北京", "广州");
        assertThat(out).extracting(row -> row.getBigDecimal("value").toString()).containsExactly("10", "30");
    }

    @Test
    void appliesNestedAndOrTree() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("city", "北京", "amount", 10, "grade", "A"));
        rows.add(rowOf("city", "北京", "amount", 90, "grade", "B"));
        rows.add(rowOf("city", "上海", "amount", 50, "grade", "A"));

        // (city=北京 AND amount>50) OR (city=上海 AND grade=A)
        JSONObject innerAnd1 = new JSONObject();
        innerAnd1.put("op", "and");
        innerAnd1.put("when", JSONArray.of(leaf("city", "eq", "北京"), leaf("amount", "gt", 50)));
        JSONObject innerAnd2 = new JSONObject();
        innerAnd2.put("op", "and");
        innerAnd2.put("when", JSONArray.of(leaf("city", "eq", "上海"), leaf("grade", "eq", "A")));

        JSONObject config = config("[{field:'city'}]", "[{field:'amount',agg:'SUM'}]");
        config.put("filterTree", orTree(innerAnd1, innerAnd2));

        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("city", "text", "amount", "number", "grade", "text"), Map.of(), config, null);
        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        assertThat(out).hasSize(2);
        assertThat(out).extracting(row -> row.getBigDecimal("value").toString()).containsExactly("90", "50");
    }

    @Test
    void havingFiltersAggregatedGroupsNotRawRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("city", "北京", "amount", 10));
        rows.add(rowOf("city", "北京", "amount", 20));
        rows.add(rowOf("city", "上海", "amount", 5));

        JSONObject config = config("[{field:'city'}]", "[{field:'amount',agg:'SUM'}]");
        config.put("having", leaf("value", "ge", 30));

        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("city", "text", "amount", "number"), Map.of(), config, null);
        @SuppressWarnings("unchecked")
        List<JSONObject> out = (List<JSONObject>) result.get("rows");
        // 北京合计 30 保留、上海 5 被筛掉：HAVING 作用于聚合值而不是原始行
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getString("dim0")).isEqualTo("北京");
        assertThat(out.get(0).getBigDecimal("value")).isEqualByComparingTo("30");
    }

    @Test
    void rejectsTooDeepTreeAndEmptyConditionGroup() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("amount", 1));

        JSONObject deep = leaf("amount", "gt", 0);
        for (int i = 0; i < 12; i++) {
            JSONObject group = new JSONObject();
            group.put("op", "and");
            group.put("when", JSONArray.of(deep));
            deep = group;
        }
        JSONObject config = config("[{field:'amount'}]", "[{field:'amount',agg:'SUM'}]");
        config.put("filterTree", deep);
        assertThatThrownBy(() -> aggregator.aggregate(rows, Map.of("amount", "number"), Map.of(), config, null))
                .isInstanceOf(com.privernal.common.core.exception.ServiceException.class)
                .hasMessageContaining("嵌套超过");

        // 空条件组不能当成「全部成立」：那只会掩盖一个配坏了的条件
        JSONObject emptyGroup = new JSONObject();
        emptyGroup.put("op", "or");
        emptyGroup.put("when", new JSONArray());
        JSONObject withEmpty = config("[{field:'amount'}]", "[{field:'amount',agg:'SUM'}]");
        withEmpty.put("filterTree", emptyGroup);
        // normalize 会把空组当未配置丢掉，所以这里不报错也不放宽——结果等于没这个条件
        Map<String, Object> lenient = aggregator.aggregate(rows, Map.of("amount", "number"), Map.of(), withEmpty, null);
        assertThat((List<?>) lenient.get("rows")).hasSize(1);
    }

    @Test
    void keepsLegacyFlatFiltersWorkingAlongsideTree() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(rowOf("city", "北京", "amount", 10));
        rows.add(rowOf("city", "上海", "amount", 20));

        // 只给旧的平铺 filters：必须仍然按 AND 生效（历史大屏的配置就是这个形状）
        JSONObject config = config("[{field:'city'}]", "[{field:'amount',agg:'SUM'}]");
        config.put("filters", JSONArray.of(leaf("city", "eq", "北京")));
        Map<String, Object> result = aggregator.aggregate(rows,
                Map.of("city", "text", "amount", "number"), Map.of(), config, null);
        assertThat((List<?>) result.get("rows")).hasSize(1);

        // 树与数组同时存在时按 AND 合并，不能静默丢掉其中一边
        JSONObject both = config("[{field:'city'}]", "[{field:'amount',agg:'SUM'}]");
        both.put("filters", JSONArray.of(leaf("city", "ne", "上海")));
        both.put("filterTree", leaf("amount", "lt", 5));
        Map<String, Object> none = aggregator.aggregate(rows,
                Map.of("city", "text", "amount", "number"), Map.of(), both, null);
        assertThat((List<?>) none.get("rows")).isEmpty();
    }
}