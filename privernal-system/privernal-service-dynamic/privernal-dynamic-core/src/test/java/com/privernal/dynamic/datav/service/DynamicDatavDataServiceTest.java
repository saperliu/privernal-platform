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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 大屏取数绑定解析单元测试（纯函数，不依赖 Spring 与数据库）
 *
 * @author saperliu
 */
@DisplayName("大屏组件绑定解析与参数映射测试")
class DynamicDatavDataServiceTest {

    private static JSONObject widget(String id, String type, JSONObject data) {
        JSONObject widget = new JSONObject();
        widget.put("id", id);
        widget.put("type", type);
        widget.put("layout", new JSONObject());
        widget.put("data", data);
        return widget;
    }

    private static JSONObject datasetData(String datasetCode) {
        JSONObject data = new JSONObject();
        data.put("mode", "dataset");
        data.put("datasetCode", datasetCode);
        return data;
    }

    private static JSONObject schemaOf(JSONObject... widgets) {
        JSONObject schema = new JSONObject();
        JSONArray array = new JSONArray();
        for (JSONObject widget : widgets) {
            array.add(widget);
        }
        schema.put("schemaVersion", 1);
        schema.put("widgets", array);
        return schema;
    }

    @Nested
    @DisplayName("resolveBindings：哪些组件该取数")
    class ResolveBindings {

        @Test
        @DisplayName("空画布与缺 widgets 都不报错，返回空清单")
        void toleratesEmptySchema() {
            assertThat(DynamicDatavDataService.resolveBindings(null)).isEmpty();

            JSONObject schema = new JSONObject();
            schema.put("schemaVersion", 1);
            assertThat(DynamicDatavDataService.resolveBindings(schema)).isEmpty();
        }

        @Test
        @DisplayName("只挑 dataset 模式，static 与缺编码的都不取数")
        void picksDatasetModeOnly() {
            JSONObject staticData = new JSONObject();
            staticData.put("mode", "static");
            staticData.put("staticRows", new JSONArray());

            JSONObject noCode = new JSONObject();
            noCode.put("mode", "dataset");
            noCode.put("datasetCode", "  ");

            List<DynamicDatavDataService.WidgetBinding> bindings = DynamicDatavDataService.resolveBindings(
                    schemaOf(widget("w1", "bar", datasetData("ds_order")),
                            widget("w2", "text", staticData),
                            widget("w3", "bar", noCode)));

            assertThat(bindings).hasSize(1);
            assertThat(bindings.get(0).widgetId()).isEqualTo("w1");
            assertThat(bindings.get(0).datasetCode()).isEqualTo("ds_order");
        }

        @Test
        @DisplayName("保持画布顺序，首屏按视觉优先级填充")
        void keepsCanvasOrder() {
            List<DynamicDatavDataService.WidgetBinding> bindings = DynamicDatavDataService.resolveBindings(
                    schemaOf(widget("w3", "bar", datasetData("ds_c")),
                            widget("w1", "bar", datasetData("ds_a")),
                            widget("w2", "bar", datasetData("ds_b"))));

            assertThat(bindings).extracting(DynamicDatavDataService.WidgetBinding::widgetId)
                    .containsExactly("w3", "w1", "w2");
        }

        @Test
        @DisplayName("缺 id、缺 data、非法元素一律跳过，不影响同屏其他组件")
        void skipsMalformedWidgets() {
            JSONObject widgets = new JSONObject();
            JSONArray array = new JSONArray();
            array.add(widget("", "bar", datasetData("ds_a")));
            JSONObject noData = new JSONObject();
            noData.put("id", "w2");
            array.add(noData);
            array.add(null);
            array.add(widget("w3", "bar", datasetData("ds_b")));
            widgets.put("widgets", array);

            List<DynamicDatavDataService.WidgetBinding> bindings = DynamicDatavDataService.resolveBindings(widgets);

            assertThat(bindings).extracting(DynamicDatavDataService.WidgetBinding::widgetId)
                    .containsExactly("w3");
        }

        @Test
        @DisplayName("reportConfig 与 paramBindings 原样带出，交给内核校验")
        void carriesConfig() {
            JSONObject data = datasetData("ds_order");
            JSONObject config = new JSONObject();
            config.put("reportType", "line");
            data.put("reportConfig", config);
            JSONArray bindingsJson = new JSONArray();
            JSONObject binding = new JSONObject();
            binding.put("param", "org");
            binding.put("field", "org_id");
            bindingsJson.add(binding);
            data.put("paramBindings", bindingsJson);

            List<DynamicDatavDataService.WidgetBinding> resolved = DynamicDatavDataService.resolveBindings(
                    schemaOf(widget("w1", "line", data)));

            assertThat(resolved).hasSize(1);
            assertThat(resolved.get(0).reportConfig().getString("reportType")).isEqualTo("line");
            assertThat(resolved.get(0).paramBindings()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("buildRuntimeFilters：参数缺失必须是错误，不是「不过滤」")
    class BuildRuntimeFilters {

        private JSONArray bindings(String... kv) {
            JSONArray array = new JSONArray();
            for (int i = 0; i < kv.length; i += 3) {
                JSONObject item = new JSONObject();
                item.put("param", kv[i]);
                item.put("field", kv[i + 1]);
                if (!kv[i + 2].isEmpty()) {
                    item.put("op", kv[i + 2]);
                }
                array.add(item);
            }
            return array;
        }

        @Test
        @DisplayName("无绑定时返回空数组，内核按无过滤处理")
        void emptyBindings() {
            assertThat(DynamicDatavDataService.buildRuntimeFilters(null, null)).isEmpty();
            assertThat(DynamicDatavDataService.buildRuntimeFilters(new JSONArray(), new JSONObject())).isEmpty();
        }

        @Test
        @DisplayName("缺省操作符为 eq，大小写与非 eq 操作符被归一")
        void normalizesOperator() {
            JSONArray filters = DynamicDatavDataService.buildRuntimeFilters(
                    bindings("org", "org_id", "", "name", "org_name", "LIKE"),
                    params("org", 88L, "name", "华东"));

            assertThat(filters).hasSize(2);
            assertThat(filters.getJSONObject(0).getString("op")).isEqualTo("eq");
            assertThat(filters.getJSONObject(0).get("value")).isEqualTo(88L);
            assertThat(filters.getJSONObject(1).getString("op")).isEqualTo("like");
        }

        @Test
        @DisplayName("参数没传值时报错，绝不静默放宽过滤范围")
        void rejectsMissingValue() {
            assertThatThrownBy(() -> DynamicDatavDataService.buildRuntimeFilters(
                    bindings("org", "org_id", ""), new JSONObject()))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("缺少大屏参数");

            JSONObject blank = new JSONObject();
            blank.put("org", "   ");
            assertThatThrownBy(() -> DynamicDatavDataService.buildRuntimeFilters(bindings("org", "org_id", ""), blank))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("org");
        }

        @Test
        @DisplayName("required:false 才允许缺值跳过")
        void optionalBindingSkipsWhenAbsent() {
            JSONObject binding = new JSONObject();
            binding.put("param", "keyword");
            binding.put("field", "order_no");
            binding.put("op", "like");
            binding.put("required", false);
            JSONArray bindings = new JSONArray();
            bindings.add(binding);

            assertThat(DynamicDatavDataService.buildRuntimeFilters(bindings, new JSONObject())).isEmpty();
            assertThat(DynamicDatavDataService.buildRuntimeFilters(bindings, params("keyword", "A1")))
                    .hasSize(1);
        }

        @Test
        @DisplayName("白名单外的操作符直接拒绝，不拼进 SQL")
        void rejectsUnknownOperator() {
            assertThatThrownBy(() -> DynamicDatavDataService.buildRuntimeFilters(
                    bindings("org", "org_id", "in"), params("org", 1)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("不支持的过滤操作符");
        }

        @Test
        @DisplayName("绑定自身缺 param/field 视为配置错误")
        void rejectsIncompleteBinding() {
            JSONArray bindings = new JSONArray();
            JSONObject onlyParam = new JSONObject();
            onlyParam.put("param", "org");
            bindings.add(onlyParam);

            assertThatThrownBy(() -> DynamicDatavDataService.buildRuntimeFilters(bindings, params("org", 1)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("参数绑定缺少");
        }

        @Test
        @DisplayName("对象/数组参数拒绝：内核只做单值绑定")
        void rejectsNonScalarValue() {
            JSONObject params = new JSONObject();
            params.put("org", new JSONArray().fluentAdd(1));

            assertThatThrownBy(() -> DynamicDatavDataService.buildRuntimeFilters(
                    bindings("org", "org_id", ""), params))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("必须是单值");
        }

        private JSONObject params(String key, Object value, String... more) {
            JSONObject params = new JSONObject();
            params.put(key, value);
            for (int i = 0; i + 1 < more.length; i += 2) {
                params.put(more[i], more[i + 1]);
            }
            return params;
        }
    }

    @Nested
    @DisplayName("applyDrillLevel：下钻只能落在组件自己声明的链上")
    class DrillLevel {

        private JSONArray chain(String... fields) {
            JSONArray array = new JSONArray();
            for (String field : fields) {
                array.add(field);
            }
            return array;
        }

        private JSONObject dim(String field) {
            JSONObject dim = new JSONObject();
            dim.put("field", field);
            dim.put("granularity", "month");
            dim.put("lookup", new JSONObject().fluentPut("targetTableCode", "stu_info"));
            return dim;
        }

        private JSONObject configOf(String... dimensionFields) {
            JSONArray dimensions = new JSONArray();
            for (String field : dimensionFields) {
                dimensions.add(dim(field));
            }
            JSONObject config = new JSONObject();
            config.put("dimensions", dimensions);
            config.put("measures", new JSONArray());
            return config;
        }

        @Test
        @DisplayName("0 层原样返回同一个对象：不改写就不该换缓存键")
        void levelZeroKeepsInstance() {
            JSONObject config = configOf("apply_time");

            assertThat(DynamicDatavDataService.applyDrillLevel(config, chain("apply_time", "class_name"), 0))
                    .isSameAs(config);
            assertThat(DynamicDatavDataService.applyDrillLevel(config, null, 0)).isSameAs(config);
        }

        @Test
        @DisplayName("换维度只带 field：granularity 与 lookup 是原字段的属性，跟过去就是没人配过的口径")
        void replacesFieldOnly() {
            JSONObject drilled = DynamicDatavDataService.applyDrillLevel(
                    configOf("apply_time"), chain("apply_time", "class_name"), 1);

            JSONObject dimension = drilled.getJSONArray("dimensions").getJSONObject(0);
            assertThat(dimension.getString("field")).isEqualTo("class_name");
            assertThat(dimension).doesNotContainKeys("granularity", "lookup");
        }

        @Test
        @DisplayName("绝不回写 schema：那份 config 就是画布对象本身")
        void neverMutatesSchema() {
            JSONObject config = configOf("apply_time");

            DynamicDatavDataService.applyDrillLevel(config, chain("apply_time", "class_name"), 1);

            JSONObject original = config.getJSONArray("dimensions").getJSONObject(0);
            assertThat(original.getString("field")).isEqualTo("apply_time");
            assertThat(original).containsKeys("granularity", "lookup");
        }

        @Test
        @DisplayName("多系列只换顶层维度，其余维度原样保留")
        void keepsOtherDimensions() {
            JSONObject drilled = DynamicDatavDataService.applyDrillLevel(
                    configOf("apply_time", "week_code"), chain("apply_time", "class_name"), 1);

            JSONArray dimensions = drilled.getJSONArray("dimensions");
            assertThat(dimensions).hasSize(2);
            assertThat(dimensions.getJSONObject(1).getString("field")).isEqualTo("week_code");
        }

        @Test
        @DisplayName("未声明链、层级越界、没有维度都明确报错，不悄悄退回顶层")
        void rejectsOutOfRange() {
            assertThatThrownBy(() -> DynamicDatavDataService.applyDrillLevel(configOf("a"), null, 1))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("未声明下钻链");
            assertThatThrownBy(() -> DynamicDatavDataService.applyDrillLevel(configOf("a"), chain("a", "b"), 2))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("超出");
            assertThatThrownBy(() -> DynamicDatavDataService.applyDrillLevel(new JSONObject(), chain("a", "b"), 1))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("没有维度");
            assertThatThrownBy(() -> DynamicDatavDataService.applyDrillLevel(configOf("a"), chain("a", " "), 1))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("字段为空");
        }

        @Test
        @DisplayName("批量层级里的脏值按 0 层：一次状态残留不该让整屏少一个组件")
        void toleratesDirtyDrillMap() {
            Map<String, Object> drills = new HashMap<>();
            drills.put("w_num", 2);
            drills.put("w_text", "3");
            drills.put("w_neg", -1);
            drills.put("w_junk", "abc");
            drills.put("w_null", null);

            assertThat(DynamicDatavDataService.drillLevelOf(drills, "w_num")).isEqualTo(2);
            assertThat(DynamicDatavDataService.drillLevelOf(drills, "w_text")).isEqualTo(3);
            assertThat(DynamicDatavDataService.drillLevelOf(drills, "w_neg")).isZero();
            assertThat(DynamicDatavDataService.drillLevelOf(drills, "w_junk")).isZero();
            assertThat(DynamicDatavDataService.drillLevelOf(drills, "w_null")).isZero();
            assertThat(DynamicDatavDataService.drillLevelOf(drills, "absent")).isZero();
            assertThat(DynamicDatavDataService.drillLevelOf(null, "w_num")).isZero();
        }

        @Test
        @DisplayName("resolveBindings 把下钻链带出来")
        void resolvesChain() {
            JSONObject data = datasetData("ds_order");
            data.put("drillFields", chain("course_name", "class_name"));

            List<DynamicDatavDataService.WidgetBinding> bindings = DynamicDatavDataService.resolveBindings(
                    schemaOf(widget("w1", "bar", data)));

            assertThat(bindings).hasSize(1);
            assertThat(bindings.get(0).drillFields()).containsExactly("course_name", "class_name");
        }
    }
}
