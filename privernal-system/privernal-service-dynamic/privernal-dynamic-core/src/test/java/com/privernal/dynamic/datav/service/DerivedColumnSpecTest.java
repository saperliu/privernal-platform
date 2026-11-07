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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 派生列（计算字段）合并与校验测试（M4.2）
 * <p>
 * 这里守的是保存动作：列快照每次保存都按数据源重算，只认识物理列。合并漏了会把用户
 * 的计算字段静默抹掉，合并松了会让计算字段盖掉真实数据列——两种都表现为「图表突然查不到数」。
 *
 * @author saperliu
 */
@DisplayName("派生列合并与校验测试")
class DerivedColumnSpecTest {

    private static JSONObject column(String code, String type, String expression) {
        JSONObject item = new JSONObject();
        item.put("fieldCode", code);
        item.put("fieldName", code);
        item.put("dataType", type);
        if (expression != null) {
            item.put("expression", expression);
        }
        return item;
    }

    private static JSONObject snapshot(JSONObject... columns) {
        JSONArray array = new JSONArray();
        for (JSONObject column : columns) {
            array.add(column);
        }
        JSONObject snapshot = new JSONObject();
        snapshot.put("columns", array);
        return snapshot;
    }

    @Nested
    @DisplayName("合并回列快照")
    class Merge {

        @Test
        @DisplayName("计算字段并到物理列之后，顺序保持")
        void appendsInSubmittedOrder() {
            JSONArray physical = new JSONArray();
            physical.add(column("amount", "number", null));
            JSONArray submitted = new JSONArray();
            submitted.add(column("net", "number", "[amount] * 0.9"));
            submitted.add(column("net2", "number", "[net] * 2"));

            DerivedColumnSpec.mergeInto(physical, submitted);

            assertThat(physical).hasSize(3);
            assertThat(physical.getJSONObject(2).getString("fieldCode")).isEqualTo("net2");
        }

        @Test
        @DisplayName("与物理列同名的计算字段直接拒绝，不静默丢弃")
        void rejectsNameClashWithPhysicalColumn() {
            JSONArray physical = new JSONArray();
            physical.add(column("amount", "number", null));
            JSONArray submitted = new JSONArray();
            submitted.add(column("amount", "number", "[qty] * 2"));

            assertThatThrownBy(() -> DerivedColumnSpec.mergeInto(physical, submitted))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("与已有列重名");
            assertThat(physical).hasSize(1);
        }

        @Test
        @DisplayName("不带 expression 的提交项不参与合并（那是重算出来的物理列）")
        void ignoresPlainColumns() {
            JSONArray physical = new JSONArray();
            physical.add(column("amount", "number", null));
            JSONArray submitted = new JSONArray();
            submitted.add(column("amount", "number", null));

            DerivedColumnSpec.mergeInto(physical, submitted);

            assertThat(physical).hasSize(1);
        }
    }

    @Nested
    @DisplayName("读取提交体")
    class Submitted {

        @Test
        @DisplayName("只挑出带表达式的列")
        void picksOnlyDerived() {
            JSONArray derived = DerivedColumnSpec.submittedDerived(
                    snapshot(column("amount", "number", null), column("net", "number", "[amount] - 1")));

            assertThat(derived).hasSize(1);
            assertThat(derived.getJSONObject(0).getString("fieldCode")).isEqualTo("net");
        }

        @Test
        @DisplayName("列项不是对象时报出位置，而不是抛类型转换异常")
        void rejectsNonObjectColumnEntry() {
            JSONArray columns = new JSONArray();
            columns.add(new JSONArray().fluentAdd("nested by mistake"));
            JSONObject snapshot = new JSONObject();
            snapshot.put("columns", columns);

            assertThatThrownBy(() -> DerivedColumnSpec.submittedDerived(snapshot))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("第 1 项");
        }

        @Test
        @DisplayName("Jackson 绑定的嵌套 LinkedHashMap 也能读（@RequestBody 只有最外层是 fastjson2）")
        void readsJacksonBoundNestedMap() {
            Map<String, Object> leaf = new LinkedHashMap<>();
            leaf.put("fieldCode", "net");
            leaf.put("dataType", "number");
            leaf.put("expression", "[amount] * 2");
            JSONObject snapshot = new JSONObject();
            snapshot.put("columns", new java.util.ArrayList<>(java.util.List.of(leaf)));

            assertThat(DerivedColumnSpec.submittedDerived(snapshot)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("表达式校验")
    class Validate {

        @Test
        @DisplayName("可引用排在它前面的计算字段，不可引用后面的")
        void forwardReferenceIsRejected() {
            Map<String, JSONObject> derived = new LinkedHashMap<>();
            derived.put("a", column("a", "number", "[b] + 1"));
            derived.put("b", column("b", "number", "[amount] * 2"));
            Map<String, String> base = new LinkedHashMap<>();
            base.put("amount", "number");

            assertThatThrownBy(() -> DerivedColumnSpec.validate(derived, base))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("计算字段「a」");
        }

        @Test
        @DisplayName("链式引用按快照顺序累积可见列")
        void chainedReferencePasses() {
            Map<String, JSONObject> derived = new LinkedHashMap<>();
            derived.put("a", column("a", "number", "[amount] * 2"));
            derived.put("b", column("b", "number", "[a] + 1"));
            Map<String, String> base = new LinkedHashMap<>();
            base.put("amount", "number");

            assertThat(DerivedColumnSpec.validate(derived, base)).containsKeys("a", "b");
        }
    }

    @Nested
    @DisplayName("SQL 翻译")
    class Sql {

        @Test
        @DisplayName("链式计算字段递归展开，不把内层派生列当列名送进 SQL")
        void expandsChainedReference() {
            Map<String, JSONObject> derived = new LinkedHashMap<>();
            derived.put("net", column("net", "number", "[amount] * 2"));
            derived.put("net2", column("net2", "number", "[net] + 1"));
            Map<String, String> base = new LinkedHashMap<>();
            base.put("amount", "number");
            Map<String, ComputeExpression.Node> nodes = DerivedColumnSpec.validate(derived, base);

            String sql = DerivedColumnSpec.sqlExpr("net2", nodes, field -> "`" + field + "`");

            assertThat(sql).contains("`amount`");
            assertThat(sql).doesNotContain("`net`").doesNotContain("`net2`");
        }

        @Test
        @DisplayName("普通列原样交给调用方的标识符解析")
        void plainColumnUsesGivenResolver() {
            Map<String, ComputeExpression.Node> nodes = DerivedColumnSpec.validate(
                    new LinkedHashMap<>(), Map.of("amount", "number"));

            assertThat(DerivedColumnSpec.sqlExpr("amount", nodes, field -> "[" + field + "]"))
                    .isEqualTo("[amount]");
        }
    }
}
