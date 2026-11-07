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
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数值分桶测试（M4.1）
 * <p>
 * 边界归属是这里唯一容易出错的地方：SQL 侧写成 {@code >=} / {@code <}、内存侧写成
 * {@code >} / {@code <=}，同一档的数量就会在两种数据源之间悄悄差一档。
 *
 * @author saperliu
 */
@DisplayName("数值分桶测试")
class ComputeBucketTest {

    private static JSONObject bucket(String elseLabel, Object[][] bins) {
        JSONArray array = new JSONArray();
        for (Object[] bin : bins) {
            JSONObject item = new JSONObject();
            item.put("label", bin[0]);
            if (bin[1] != null) {
                item.put("min", bin[1]);
            }
            if (bin[2] != null) {
                item.put("max", bin[2]);
            }
            array.add(item);
        }
        JSONObject bucket = new JSONObject();
        bucket.put("bins", array);
        if (elseLabel != null) {
            bucket.put("elseLabel", elseLabel);
        }
        return bucket;
    }

    private static final Object[][] AMOUNT_BINS = {
            {"小", null, 100},
            {"中", 100, 500},
            {"大", 500, null}
    };

    @Test
    @DisplayName("边界值按左闭右开归档，与 SQL 侧 CASE WHEN 完全一致")
    void boundaryBelongsToUpperBin() {
        JSONObject parsed = ComputeBucket.parse(dimWith(AMOUNT_BINS, null));

        assertThat(ComputeBucket.labelOf(99, parsed)).isEqualTo("小");
        // 100 同时是「小」的上限与「中」的下限：只能有一档收到它
        assertThat(ComputeBucket.labelOf(100, parsed)).isEqualTo("中");
        assertThat(ComputeBucket.labelOf(499.99, parsed)).isEqualTo("中");
        assertThat(ComputeBucket.labelOf(500, parsed)).isEqualTo("大");
        assertThat(ComputeBucket.labelOf(10000, parsed)).isEqualTo("大");

        String sql = ComputeBucket.caseWhen("amount", parsed);
        assertThat(sql).contains("amount >= 100").contains("amount < 500").contains("amount >= 500");
        // 上限一律用 < 而不是 <=，否则 500 会同时落进「中」和「大」
        assertThat(sql).doesNotContain("amount <= 500").doesNotContain("amount >= 500 AND");
        // 第一档只有上限，写成 <= 就会与第二档的 >= 重叠
        assertThat(sql).contains("WHEN amount < 100 THEN '小'");
    }

    @Test
    @DisplayName("非数值与区间外的值按 elseLabel 处理，未配时原样输出")
    void handlesNonNumericAndOutOfRange() {
        JSONObject withElse = ComputeBucket.parse(dimWith(new Object[][]{{"小", null, 100}}, "其他"));
        assertThat(ComputeBucket.labelOf(500, withElse)).isEqualTo("其他");
        assertThat(ComputeBucket.labelOf("abc", withElse)).isEqualTo("其他");
        assertThat(ComputeBucket.labelOf(null, withElse)).isEqualTo("其他");
        assertThat(ComputeBucket.caseWhen("amount", withElse)).endsWith("ELSE '其他' END");

        JSONObject noElse = ComputeBucket.parse(dimWith(new Object[][]{{"小", null, 100}}, null));
        assertThat(ComputeBucket.labelOf(500, noElse)).isNull();
        // 没配「其他」时 SQL 侧原样输出该值，并保持返回类型稳定（CASE 混类型在 SQLServer 上会报错）
        assertThat(ComputeBucket.caseWhen("amount", noElse)).contains("ELSE CAST(amount AS CHAR)");
    }

    @Test
    @DisplayName("档位标签里的单引号必须转义，不能改写 SQL")
    void escapesQuoteInLabels() {
        JSONObject parsed = ComputeBucket.parse(dimWith(new Object[][]{{"O'Brien", null, 100}}, null));
        String sql = ComputeBucket.caseWhen("amount", parsed);
        assertThat(sql).contains("'O''Brien'");
        // 未转义的话这条 SQL 的 CASE 分支会被截断
        assertThat(sql.split("'O''Brien'", -1)).hasSize(2);
    }

    @Test
    @DisplayName("非法桶定义在建查询之前就被拒")
    void rejectsBrokenBins() {
        // 下限不小于上限：这样的区间永远命中不了，静默接受等于给一个恒为「其他」的图
        assertThatThrownBy(() -> ComputeBucket.parse(dimWith(new Object[][]{{"坏", 500, 100}}, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("下限必须小于上限");

        assertThatThrownBy(() -> ComputeBucket.parse(dimWith(new Object[][]{{"空档", null, null}}, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("至少要给一个边界");

        assertThatThrownBy(() -> ComputeBucket.parse(dimWith(new Object[][]{{null, 1, 2}}, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少标签");

        JSONObject noBins = new JSONObject();
        noBins.put("bucket", new JSONObject());
        assertThat(ComputeBucket.parse(noBins)).isNull();
    }

    @Test
    @DisplayName("没配分桶时两侧都是原样")
    void noBucketIsPassThrough() {
        assertThat(ComputeBucket.parse(new JSONObject())).isNull();
        assertThat(ComputeBucket.caseWhen("amount", null)).isEqualTo("amount");
        assertThat(ComputeBucket.labelOf(1, null)).isNull();
    }

    private static JSONObject dimWith(Object[][] bins, String elseLabel) {
        JSONObject dim = new JSONObject();
        dim.put("bucket", bucket(elseLabel, bins));
        return dim;
    }
}
