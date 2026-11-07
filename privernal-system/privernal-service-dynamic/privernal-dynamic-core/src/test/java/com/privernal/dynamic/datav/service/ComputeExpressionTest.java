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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 派生列表达式测试（M4.2）
 * <p>
 * 最有价值的一组是「同一棵 AST 的两个后端语义一致」：SQL 侧下推、内存侧逐行算，
 * 只要两边结论不同，同一数据集就会因为源类型不同给出两个数——而这正是 M4 验收要防的事。
 *
 * @author saperliu
 */
@DisplayName("派生列表达式测试")
class ComputeExpressionTest {

    private static final Map<String, String> COLUMNS = Map.of(
            "amount", "number", "cost", "number", "qty", "number",
            // note 故意不出现在测试行里：COALESCE / ISBLANK 需要一个真实存在的可空列，
            // 而不是引用一个不存在的列（后者会被字段校验直接拒掉）
            "name", "text", "start_time", "date", "city", "text", "note", "text");

    private static ComputeExpression.Node parse(String expression) {
        return ComputeExpression.parse(expression, COLUMNS);
    }

    private static Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", new BigDecimal("100.50"));
        row.put("cost", new BigDecimal("30.25"));
        row.put("qty", 3);
        row.put("name", "Ada");
        row.put("start_time", "2026-10-24 22:15:00");
        row.put("city", "北京");
        return row;
    }

    @Test
    @DisplayName("算术与优先级、括号")
    void evaluatesArithmeticWithPrecedence() {
        assertThat(parse("[amount] - [cost]").evaluate(row())).isEqualTo(new BigDecimal("70.25"));
        // 不处理优先级就会把 100.5 - 30.25 * 3 算成 (100.5-30.25)*3
        assertThat(parse("[amount] - [cost] * [qty]").evaluate(row()))
                .isEqualTo(new BigDecimal("100.50").subtract(new BigDecimal("30.25").multiply(new BigDecimal(3))));
        assertThat(parse("([amount] - [cost]) * [qty]").evaluate(row()))
                .isEqualTo(new BigDecimal("70.25").multiply(new BigDecimal(3)));
    }

    @Test
    @DisplayName("SQL 翻译形状：括号、<>、AND/OR、CASE")
    void translatesToExpectedSql() {
        assertThat(parse("[amount] - [cost]").toSql(field -> field))
                .isEqualTo("(amount-cost)");
        assertThat(parse("[amount] != [cost]").toSql(field -> field)).contains("<>");
        assertThat(parse("[qty] > 1 && [city] = '北京'").toSql(field -> field)).contains(" AND ");
        assertThat(parse("[qty] > 1 || [city] = '上海'").toSql(field -> field)).contains(" OR ");
        assertThat(parse("IF([qty] > 1, '多', '少')").toSql(field -> field))
                .contains("CASE WHEN")
                .contains("THEN '多'")
                .contains("ELSE '少'")
                .contains("END");
        assertThat(parse("ISBLANK([city])").toSql(field -> field)).contains("IS NULL");
        // 字段引用要交给调用方的解析器（派生列套派生列时由它继续展开）
        assertThat(parse("[amount] * 2").toSql(field -> "t_" + field)).isEqualTo("(t_amount*2)");
    }

    @Test
    @DisplayName("函数：条件、空值兜底、文本、取整、时间部分")
    void supportsTheFunctionSet() {
        Map<String, Object> row = row();
        // 字面量统一解析成 BigDecimal（不让整数与小数走两套类型），所以按值比而不是按类型比
        assertThat((BigDecimal) parse("IF([qty] > 2, 1, 0)").evaluate(row)).isEqualByComparingTo("1");
        assertThat((BigDecimal) parse("IF([qty] > 9, 1, 0)").evaluate(row)).isEqualByComparingTo("0");
        assertThat(parse("COALESCE([note], '默认')").evaluate(row)).isEqualTo("默认");
        assertThat(parse("ISBLANK([note])").evaluate(row)).isEqualTo(true);
        assertThat(parse("CONCAT([name], '-', [city])").evaluate(row)).isEqualTo("Ada-北京");
        assertThat(parse("UPPER([name])").evaluate(row)).isEqualTo("ADA");
        assertThat(parse("ROUND([amount], 1)").evaluate(row)).isEqualTo(new BigDecimal("100.5"));
        assertThat(parse("FLOOR([amount])").evaluate(row)).isEqualTo(new BigDecimal("100"));
        assertThat(parse("CEIL([amount])").evaluate(row)).isEqualTo(new BigDecimal("101"));
        assertThat(parse("ABS([cost] - [amount])").evaluate(row)).isEqualTo(new BigDecimal("70.25"));
        assertThat(parse("YEAR([start_time])").evaluate(row)).isEqualTo(2026L);
        assertThat(parse("MONTH([start_time])").evaluate(row)).isEqualTo(10L);
        assertThat(parse("HOUR([start_time])").evaluate(row)).isEqualTo(22L);
        assertThat(parse("MINUTE([start_time])").evaluate(row)).isEqualTo(15L);
        assertThat(parse("LENGTH([city])").evaluate(row)).isEqualTo(2L);
        assertThat(parse("ISBLANK([city])").evaluate(row)).isEqualTo(false);
    }

    @Test
    @DisplayName("字符串比较忽略大小写，与 MySQL 默认排序规则一致")
    void stringCompareIsCaseInsensitiveLikeMysqlCollation() {
        Map<String, Object> row = row();
        // 大小写敏感的 Java equals 会让这条在动态表（SQL）上成立、在外部源（内存）上不成立
        assertThat(parse("[name] = 'ada'").evaluate(row)).isEqualTo(true);
        assertThat(parse("[city] = '北京'").evaluate(row)).isEqualTo(true);
        assertThat(parse("[city] != '上海'").evaluate(row)).isEqualTo(true);
    }

    @Test
    @DisplayName("数值比较按值而不是按字符串")
    void numericCompareUsesValueNotText() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", "1.0");
        row.put("cost", new BigDecimal("1.00"));
        assertThat(parse("[amount] = [cost]").evaluate(row)).isEqualTo(true);
        assertThat(parse("[amount] >= [cost]").evaluate(row)).isEqualTo(true);
    }

    @Test
    @DisplayName("拒绝未知函数、非法字段与残缺语法")
    void rejectsUnsupportedExpressions() {
        assertThatThrownBy(() -> parse("MYSTERY([amount])"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持的函数或关键字");
        assertThatThrownBy(() -> parse("[not_a_column] + 1"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不存在于数据集列快照");
        assertThatThrownBy(() -> parse("([amount] + 1"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("右括号");
        assertThatThrownBy(() -> parse("[amount] +"))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("不执行任意代码：反射、类加载、脚本注入一律进不来")
    void cannotReachCodeExecution() {
        // 受限语法里没有任何形式能触达类或方法：既没有点号取成员，也没有标识符调用未白名单函数
        for (String payload : new String[]{
                "Runtime.getRuntime().exec('id')",
                "[amount].class.forName('x')",
                "new java.io.File('/etc/passwd')",
                "System.exit(0)",
                "${T(java.lang.Runtime).getRuntime()}"}) {
            assertThatThrownBy(() -> parse(payload))
                    .as("payload=%s", payload)
                    .isInstanceOf(ServiceException.class);
        }
    }

    @Test
    @DisplayName("字符串字面量里的单引号不会改写 SQL 结构")
    void quotesInLiteralAreEscaped() {
        String sql = parse("IF([city] = 'O\\'Brien', 1, 0)").toSql(field -> field);
        assertThat(sql).contains("''");
        // 未转义时这一条 CASE 会被截断成两条语句片段
        assertThat(sql).doesNotContain("'O'Brien'");
    }

    @Test
    @DisplayName("超长表达式被拒：解析是递归的，长度就是天然资源上限")
    void rejectsOverlongExpression() {
        String deep = "[amount]" + " + [cost]".repeat(ComputeExpression.MAX_LENGTH);
        assertThatThrownBy(() -> parse(deep))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最长");
    }

    @Test
    @DisplayName("派生列可以引用先前的派生列，重名与非法引用被拒")
    void derivedColumnsChainAndValidate() {
        Map<String, com.alibaba.fastjson2.JSONObject> derived = new LinkedHashMap<>();
        derived.put("profit", column("profit", "利润", "number", "[amount] - [cost]"));
        derived.put("profit_label", column("profit_label", "利润档", "text",
                "IF([profit] > 50, '高', '低')"));
        Map<String, ComputeExpression.Node> parsed =
                DerivedColumnSpec.validate(derived, COLUMNS);
        assertThat(parsed).containsKeys("profit", "profit_label");

        Map<String, Object> row = row();
        DerivedColumnSpec.applyToRows(new java.util.ArrayList<>(java.util.List.of(row)), parsed, COLUMNS);
        assertThat((BigDecimal) row.get("profit")).isEqualByComparingTo("70.25");
        assertThat(row.get("profit_label")).isEqualTo("高");

        // 与已有列重名会覆盖真实数据，必须拒
        Map<String, com.alibaba.fastjson2.JSONObject> clash = new LinkedHashMap<>();
        clash.put("amount", column("amount", "冒名", "number", "[cost] * 2"));
        assertThatThrownBy(() -> DerivedColumnSpec.validate(clash, COLUMNS))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("重名");

        // 派生列引用尚未定义的列，报错要带上是哪个计算字段，而不是裸的列名错误
        Map<String, com.alibaba.fastjson2.JSONObject> bad = new LinkedHashMap<>();
        bad.put("x", column("x", "坏字段", "number", "[nope] + 1"));
        assertThatThrownBy(() -> DerivedColumnSpec.validate(bad, COLUMNS))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("坏字段");
    }

    private static com.alibaba.fastjson2.JSONObject column(String code, String name, String type, String expression) {
        com.alibaba.fastjson2.JSONObject json = new com.alibaba.fastjson2.JSONObject();
        json.put("fieldCode", code);
        json.put("fieldName", name);
        json.put("dataType", type);
        json.put("expression", expression);
        return json;
    }
}
