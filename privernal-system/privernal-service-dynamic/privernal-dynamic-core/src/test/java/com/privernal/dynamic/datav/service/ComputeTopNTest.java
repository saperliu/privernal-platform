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
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TopN 与「其他」归并测试（M4.4）
 * <p>
 * 重点是 AVG 的加权：直接对分组平均值再取平均，会让一行数据的组与一万行的组等权，
 * 这类数字看起来完全正常，只有对着原始数据核才看得出来。
 *
 * @author saperliu
 */
@DisplayName("TopN 与其他归并测试")
class ComputeTopNTest {

    private static JSONObject row(String dim, String value, String weight) {
        JSONObject row = new JSONObject();
        row.put("dim0", dim);
        row.put("value", new BigDecimal(value));
        row.put(ComputeTopN.WEIGHT_ALIAS, new BigDecimal(weight));
        return row;
    }

    private static Map<String, String> aggOf(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static JSONObject topN(int n, String mergeAs) {
        JSONObject config = new JSONObject();
        config.put("by", "value");
        config.put("n", n);
        if (mergeAs != null) {
            config.put("mergeRestAs", mergeAs);
        }
        return config;
    }

    @Test
    @DisplayName("纯截断：按 by 降序保留前 N 组，不产生归并行")
    void truncatesWithoutMerge() {
        List<JSONObject> rows = new ArrayList<>(List.of(
                row("A", "30", "1"), row("B", "10", "1"), row("C", "50", "1"), row("D", "5", "1")));

        ComputeTopN.apply(rows, topN(2, null), aggOf("value", "SUM"), "dim0");

        assertThat(rows).extracting(row -> row.getString("dim0")).containsExactly("C", "A");
    }

    @Test
    @DisplayName("SUM 与 COUNT 可直接相加归并")
    void mergesSumAndCount() {
        List<JSONObject> rows = new ArrayList<>(List.of(
                row("A", "100", "10"), row("B", "30", "3"), row("C", "20", "2")));

        ComputeTopN.apply(rows, topN(1, "其他"), aggOf("value", "SUM"), "dim0");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getString("dim0")).isEqualTo("其他");
        assertThat(rows.get(1).getBigDecimal("value")).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("AVG 按组内行数加权，而不是对分组均值再取均值")
    void averagesByWeightNotByGroupCount() {
        // 保留 A（均值最大）；「其他」由 B(100 行、均值 10) 与 C(1 行、均值 20) 合成。
        // 简单平均是 15，按行数加权是 1020/101≈10.0990——差得远，且 15 看起来完全正常，
        // 这正是归并「其他」最容易悄悄做错的地方
        List<JSONObject> rows = new ArrayList<>(List.of(
                row("A", "90", "5"), row("B", "10", "100"), row("C", "20", "1")));

        ComputeTopN.apply(rows, topN(1, "其他"), aggOf("value", "AVG"), "dim0");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getString("dim0")).isEqualTo("A");
        assertThat(rows.get(1).getString("dim0")).isEqualTo("其他");
        assertThat(rows.get(1).getBigDecimal("value")).isEqualByComparingTo("10.0990");
    }

    @Test
    @DisplayName("MAX/MIN 归并取极值")
    void mergesMaxAndMin() {
        List<JSONObject> maxRows = new ArrayList<>(List.of(
                row("A", "100", "1"), row("B", "30", "1"), row("C", "70", "1")));
        ComputeTopN.apply(maxRows, topN(1, "其他"), aggOf("value", "MAX"), "dim0");
        assertThat(maxRows.get(1).getBigDecimal("value")).isEqualByComparingTo("70");

        List<JSONObject> minRows = new ArrayList<>(List.of(
                row("A", "100", "1"), row("B", "30", "1"), row("C", "70", "1")));
        ComputeTopN.apply(minRows, topN(1, "其他"), aggOf("value", "MIN"), "dim0");
        assertThat(minRows.get(1).getBigDecimal("value")).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("去重计数不能归并：从分组结果推不出正确的值，必须拒绝而不是给一个近似数")
    void refusesToMergeCountDistinct() {
        List<JSONObject> rows = new ArrayList<>(List.of(
                row("A", "100", "1"), row("B", "3", "1"), row("C", "4", "1")));

        assertThatThrownBy(() -> ComputeTopN.apply(rows, topN(1, "其他"), aggOf("value", "COUNTD"), "dim0"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无法归并");
    }

    @Test
    @DisplayName("缺权重列时 AVG 归并直接失败")
    void refusesUnweightedAverage() {
        List<JSONObject> rows = new ArrayList<>();
        for (String name : List.of("A", "B", "C")) {
            JSONObject row = new JSONObject();
            row.put("dim0", name);
            row.put("value", new BigDecimal("10"));
            rows.add(row);
        }
        assertThatThrownBy(() -> ComputeTopN.apply(rows, topN(1, "其他"), aggOf("value", "AVG"), "dim0"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("权重");
    }

    @Test
    @DisplayName("分组数不超过 N 时原样返回，不产生空的「其他」行")
    void keepsRowsWhenNothingToFold() {
        List<JSONObject> rows = new ArrayList<>(List.of(row("A", "30", "1"), row("B", "10", "1")));

        ComputeTopN.apply(rows, topN(5, "其他"), aggOf("value", "SUM"), "dim0");

        assertThat(rows).hasSize(2);
        assertThat(rows).noneMatch(row -> "其他".equals(row.getString("dim0")));
    }

    @Test
    @DisplayName("缺值不占 TopN 名额")
    void nullValuesSortLast() {
        List<JSONObject> rows = new ArrayList<>();
        JSONObject missing = new JSONObject();
        missing.put("dim0", "空");
        missing.put(ComputeTopN.WEIGHT_ALIAS, BigDecimal.ONE);
        rows.add(missing);
        rows.add(row("A", "5", "1"));
        rows.add(row("B", "9", "1"));

        ComputeTopN.apply(rows, topN(2, null), aggOf("value", "SUM"), "dim0");

        assertThat(rows).extracting(row -> row.getString("dim0")).containsExactly("B", "A");
    }
}
