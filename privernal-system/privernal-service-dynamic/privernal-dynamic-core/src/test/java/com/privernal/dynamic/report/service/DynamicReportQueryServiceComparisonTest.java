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
import com.privernal.dynamic.datav.service.DatavDataSourceResolver;
import com.privernal.dynamic.report.domain.DynamicDataset;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 同比/环比（M3-1）纯逻辑测试：不连库，覆盖分组键解析、对齐、增速与 400 可读错误。
 * <p>
 * 对齐是两段聚合合并正确性的命门：方言输出格式差异（SQLServer 的 2026-W43、
 * PG 的小时无 :00）必须在解析处归一，否则对比段整列 Compare 全 null 还「看起来正常」。
 * run() 的 SQL 组装与执行属集成面，留给运行时验证（需 IDEA 重启后的真库）。
 *
 * @author saperliu
 */
@DisplayName("同比/环比两段聚合合并逻辑测试")
class DynamicReportQueryServiceComparisonTest {

    private DynamicReportQueryService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DynamicReportQueryService();
        // isExternalSource 只看 connectors 注册表：new 出来即为空表，全部按动态表处理
        Field resolverField = DynamicReportQueryService.class.getDeclaredField("datavDataSourceResolver");
        resolverField.setAccessible(true);
        resolverField.set(service, new DatavDataSourceResolver());
    }

    private DynamicDataset dataset(String fieldCode, String dataType) {
        DynamicDataset dataset = new DynamicDataset();
        dataset.setDatasetCode("TRANSIENT:test");
        dataset.setSourceType("dynamic");
        JSONArray columns = new JSONArray();
        JSONObject column = new JSONObject();
        column.put("fieldCode", fieldCode);
        column.put("fieldName", fieldCode);
        column.put("dataType", dataType);
        columns.add(column);
        JSONObject snapshot = new JSONObject();
        snapshot.put("columns", columns);
        dataset.setColumnSnapshot(snapshot);
        return dataset;
    }

    private JSONObject configWithDims(JSONArray dimensions) {
        JSONObject config = new JSONObject();
        config.put("reportType", "bar");
        config.put("dimensions", dimensions);
        config.put("measures", new JSONArray());
        return config;
    }

    @Nested
    @DisplayName("分组键解析：方言格式差异归一")
    class ParseGroupKey {

        @Test
        @DisplayName("DAY/MONTH/YEAR 三种标准格式")
        void standardFormats() {
            assertThat(DynamicReportQueryService.parseGroupKey("2026-10-28", "DAY")).isEqualTo(LocalDate.of(2026, 10, 28));
            assertThat(DynamicReportQueryService.parseGroupKey("2026-10", "MONTH")).isEqualTo(YearMonth.of(2026, 10));
            assertThat(DynamicReportQueryService.parseGroupKey("2026", "YEAR")).isEqualTo(Year.of(2026));
        }

        @Test
        @DisplayName("WEEK：MySQL/PG 的 2026-43 与 SQLServer 的 2026-W43 归一到同一天")
        void weekFormats() {
            Object mysql = DynamicReportQueryService.parseGroupKey("2026-43", "WEEK");
            Object sqlserver = DynamicReportQueryService.parseGroupKey("2026-W43", "WEEK");
            assertThat(mysql).isNotNull();
            assertThat(mysql).isEqualTo(sqlserver);
            // ISO 周 1 的锚点：2026-01-04（周日）恒在 ISO 第 1 周，解析还原出该周的同一个星期几
            Object week1 = DynamicReportQueryService.parseGroupKey("2026-1", "WEEK");
            assertThat(week1).isEqualTo(LocalDate.of(2026, 1, 4));
        }

        @Test
        @DisplayName("HOUR：MySQL 带分钟占位与 PG 不带的两种格式归一")
        void hourFormats() {
            Object mysql = DynamicReportQueryService.parseGroupKey("2026-10-28 09:00", "HOUR");
            Object pg = DynamicReportQueryService.parseGroupKey("2026-10-28 09", "HOUR");
            assertThat(mysql).isEqualTo(LocalDateTime.of(2026, 10, 28, 9, 0));
            assertThat(mysql).isEqualTo(pg);
        }

        @Test
        @DisplayName("解析不了的值返回 null（对齐时跳过，不让一格脏数据炸掉整图）")
        void garbageReturnsNull() {
            assertThat(DynamicReportQueryService.parseGroupKey("not-a-date", "DAY")).isNull();
            assertThat(DynamicReportQueryService.parseGroupKey("2026-1x", "WEEK")).isNull();
        }
    }

    @Nested
    @DisplayName("增速计算")
    class Growth {

        @Test
        @DisplayName("普通正增长：4 位小数 HALF_UP")
        void normalGrowth() {
            assertThat((Double) DynamicReportQueryService.growthOf(120, 100)).isEqualTo(0.2);
            assertThat((Double) DynamicReportQueryService.growthOf(100, 120)).isEqualTo(-0.1667);
        }

        @Test
        @DisplayName("对比值为 0 或缺失返回 null（前端显示「—」，不做近似）")
        void zeroOrMissing() {
            assertThat(DynamicReportQueryService.growthOf(100, 0)).isNull();
            assertThat(DynamicReportQueryService.growthOf(100, null)).isNull();
            assertThat(DynamicReportQueryService.growthOf(null, 100)).isNull();
        }
    }

    @Nested
    @DisplayName("对齐键：对比段前移一周期后与本期同键")
    class AlignKey {

        @Test
        @DisplayName("月粒度：对比段 2025-10 前移一年与本期 2026-10 同键")
        void yoyMonthly() {
            JSONArray dimensions = new JSONArray();
            dimensions.add(new JSONObject().fluentPut("field", "created_time").fluentPut("granularity", "MONTH"));
            JSONObject currentRow = new JSONObject();
            currentRow.put("dim0", "2026-10");
            currentRow.put("dim1", "A点位");
            JSONObject compareRow = new JSONObject();
            compareRow.put("dim0", "2025-10");
            compareRow.put("dim1", "A点位");

            String currentKey = service.comparisonAlignKey(currentRow, dimensions, 0, "MONTH", 0,
                    java.time.temporal.ChronoUnit.YEARS);
            String compareKey = service.comparisonAlignKey(compareRow, dimensions, 0, "MONTH", 1,
                    java.time.temporal.ChronoUnit.YEARS);
            assertThat(currentKey).isEqualTo(compareKey);
        }

        @Test
        @DisplayName("非时间维度的值参与 join 键：不同点位的对比不会串行")
        void secondaryDimsParticipate() {
            JSONArray dimensions = new JSONArray();
            dimensions.add(new JSONObject().fluentPut("field", "created_time").fluentPut("granularity", "MONTH"));
            dimensions.add(new JSONObject().fluentPut("field", "point_name"));
            JSONObject rowA = new JSONObject();
            rowA.put("dim0", "2026-10");
            rowA.put("dim1", "A点位");
            JSONObject rowB = new JSONObject();
            rowB.put("dim0", "2026-10");
            rowB.put("dim1", "B点位");

            String keyA = service.comparisonAlignKey(rowA, dimensions, 0, "MONTH", 0, java.time.temporal.ChronoUnit.YEARS);
            String keyB = service.comparisonAlignKey(rowB, dimensions, 0, "MONTH", 0, java.time.temporal.ChronoUnit.YEARS);
            assertThat(keyA).isNotEqualTo(keyB);
        }

        @Test
        @DisplayName("时间为空的行返回 null，对齐时自然落空（Compare=null）")
        void nullTimeReturnsNull() {
            JSONArray dimensions = new JSONArray();
            dimensions.add(new JSONObject().fluentPut("field", "created_time").fluentPut("granularity", "MONTH"));
            JSONObject row = new JSONObject();
            row.put("dim0", null);
            assertThat(service.comparisonAlignKey(row, dimensions, 0, "MONTH", 0,
                    java.time.temporal.ChronoUnit.YEARS)).isNull();
        }
    }

    @Nested
    @DisplayName("入口校验：400 可读错误")
    class EntryValidation {

        @Test
        @DisplayName("未知对比模式拒绝")
        void unknownMode() {
            JSONObject comparison = new JSONObject().fluentPut("mode", "qoq");
            assertThatThrownBy(() -> service.runComparison("db", dataset("created_time", "date"),
                    configWithDims(new JSONArray()), null, comparison))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("不支持的对比模式");
        }

        @Test
        @DisplayName("外部源数据集不支持对比")
        void externalSource() throws Exception {
            DatavDataSourceResolver external = new DatavDataSourceResolver();
            Field connectors = DatavDataSourceResolver.class.getDeclaredField("connectors");
            connectors.setAccessible(true);
            ((java.util.Map) connectors.get(external)).put("http", new Object());
            Field resolverField = DynamicReportQueryService.class.getDeclaredField("datavDataSourceResolver");
            resolverField.setAccessible(true);
            DynamicDataset dataset = dataset("created_time", "date");
            dataset.setSourceType("http");
            assertThatThrownBy(() -> {
                DynamicReportQueryService withExternal = new DynamicReportQueryService();
                resolverField.set(withExternal, external);
                withExternal.runComparison("db", dataset, configWithDims(new JSONArray()), null,
                        new JSONObject().fluentPut("mode", "yoy"));
            }).isInstanceOf(ServiceException.class).hasMessageContaining("外部源数据集暂不支持");
        }

        @Test
        @DisplayName("yoy/mom 没有带时间粒度的日期维度时给可读错误")
        void missingTimeDim() {
            JSONArray dimensions = new JSONArray();
            dimensions.add(new JSONObject().fluentPut("field", "name"));
            assertThatThrownBy(() -> service.runComparison("db", dataset("created_time", "date"),
                    configWithDims(dimensions), null, new JSONObject().fluentPut("mode", "yoy")))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("带时间粒度");
        }

        @Test
        @DisplayName("年粒度：yoy 无更上周期、mom 等价 yoy，都给可读引导")
        void yearGranularity() {
            JSONArray dimensions = new JSONArray();
            dimensions.add(new JSONObject().fluentPut("field", "created_time").fluentPut("granularity", "YEAR"));
            assertThatThrownBy(() -> service.runComparison("db", dataset("created_time", "date"),
                    configWithDims(dimensions), null, new JSONObject().fluentPut("mode", "yoy")))
                    .isInstanceOf(ServiceException.class).hasMessageContaining("年粒度");
            assertThatThrownBy(() -> service.runComparison("db", dataset("created_time", "date"),
                    configWithDims(dimensions), null, new JSONObject().fluentPut("mode", "mom")))
                    .isInstanceOf(ServiceException.class).hasMessageContaining("yoy");
        }

        @Test
        @DisplayName("prev 缺起止范围过滤时给可读错误")
        void prevWithoutWindow() {
            assertThatThrownBy(() -> service.runComparison("db", dataset("created_time", "date"),
                    configWithDims(new JSONArray()), null, new JSONObject().fluentPut("mode", "prev")))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("起止范围过滤");
        }

        @Test
        @DisplayName("prev 用于分组序列时拒绝（单值卡专属）")
        void prevOnGrouped() {
            JSONObject config = configWithDims(new JSONArray());
            config.put("reportType", "bar");
            JSONArray filters = new JSONArray();
            filters.add(new JSONObject().fluentPut("field", "created_time").fluentPut("op", "ge")
                    .fluentPut("value", "2026-10-01"));
            filters.add(new JSONObject().fluentPut("field", "created_time").fluentPut("op", "le")
                    .fluentPut("value", "2026-10-31"));
            config.put("filters", filters);
            assertThatThrownBy(() -> service.runComparison("db", dataset("created_time", "date"),
                    config, null, new JSONObject().fluentPut("mode", "prev")))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("stat/gauge");
        }
    }
}
