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

package com.privernal.dynamic.dba.utils;

import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.EvaluationContext;

import java.util.Map;

/**
 * 公式字段求值：表达式形如 {@code {price} * {qty} * 0.9}，{列编码} 引用本行其它字段值。
 *
 * 安全边界：SimpleEvaluationContext 的 Map 数据访问子集——类型引用、方法调用（除 Map 取值）、
 * Bean 访问全部封闭；表达式来自表单设计器（管理员可信面），但求值仍按最小能力开放，
 * 防止把设计器权限升级成任意 SpEL。
 *
 * @author saperliu
 */
public final class FormulaEvaluator {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private FormulaEvaluator() {
    }

    /**
     * 求值。未提交的字段引用解析为 null（算术运算将失败并向上抛，由保存链路回显错误——
     * 公式是业务逻辑，静默给默认值会把错算成对的数）。
     *
     * @param expression 公式（{列编码} 引用）
     * @param values     本行已提交字段值（键=列编码原样）
     * @return 求值结果（数值或字符串）
     */
    public static Object eval(String expression, Map<String, Object> values) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        // {code} → #code 变量引用
        String spel = expression.replaceAll("\\{([A-Za-z0-9_\\-]+)}", "#$1");
        Expression expr = PARSER.parseExpression(spel);
        // forReadWriteDataBinding：Spring 提供的最小能力子集——仅属性/Map 取值赋值，
        // 类型引用、构造器、任意方法调用、Bean 访问全部封闭
        EvaluationContext context = SimpleEvaluationContext.forReadWriteDataBinding().build();
        if (values != null) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }
        return expr.getValue(context);
    }
}
