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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 文本公式辅助（R2-11 文本公式分支）：只做表达式的引用提取与未提交值归一，
 * 求值仍走既有 {@link FormulaEvaluator}（沙箱语义不变）。
 *
 * <p>文本公式（props.textType）拼接场景下，未提交引用应参与为空串
 * （'前缀' + {name} 在 name 未填时应得到 '前缀' 而不是求值失败），
 * 结果统一转字符串。</p>
 *
 * @author saperliu
 */
public final class FormulaTextSupport {

    /** {列编码} 引用的提取正则（与 FormulaEvaluator 替换口径一致） */
    private static final java.util.regex.Pattern REF_PATTERN = java.util.regex.Pattern.compile("\\{([A-Za-z0-9_\\-]+)}");

    private FormulaTextSupport() {
    }

    /** 提取表达式引用的列编码集合 */
    public static Set<String> extractRefs(String expression) {
        Set<String> refs = new LinkedHashSet<>();
        if (expression == null || expression.isBlank()) {
            return refs;
        }
        java.util.regex.Matcher matcher = REF_PATTERN.matcher(expression);
        while (matcher.find()) {
            refs.add(matcher.group(1));
        }
        return refs;
    }

    /**
     * 文本模式值归一：拷贝一份 values，把表达式引用到的缺失/null 引用补为空串
     */
    public static Map<String, Object> normalizeTextValues(String expression, Map<String, Object> values) {
        Map<String, Object> normalized = values == null ? new HashMap<>() : new HashMap<>(values);
        for (String ref : extractRefs(expression)) {
            if (!normalized.containsKey(ref) || normalized.get(ref) == null) {
                normalized.put(ref, "");
            }
        }
        return normalized;
    }

    /** 结果转字符串（文本公式列是 TEXT，数值结果按文本落库） */
    public static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
