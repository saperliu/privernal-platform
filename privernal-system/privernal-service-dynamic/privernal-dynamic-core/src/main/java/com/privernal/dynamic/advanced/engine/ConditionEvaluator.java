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

package com.privernal.dynamic.advanced.engine;

import com.privernal.common.security.service.SecurityUtils;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 联动条件求值器（§7.3）：纯内存递归，杜绝 SQL 拼接。
 * <p>
 * 条件树：{@code {op: AND/OR, children:[...]}} 或叶子 {@code {field, cmp, value}}；
 * value 支持常量 / 事件数据引用 {@code {{field}}} / 上下文变量
 * {@code {{currentUser}}、{{currentDept}}、{{now}}}。
 * <p>
 * 递归防护：树深超过 {@value #MAX_DEPTH} 直接判不命中并给 trace（§7.5.3）；
 * 字段在数据里不存在按 null（IS_EMPTY 语义）处理并记入判定依据——
 * 这是 §8.2"列被删后规则看似没触发"排查黑洞的软失败口径。
 */
public final class ConditionEvaluator {

    public static final int MAX_DEPTH = 10;

    private ConditionEvaluator() {
    }

    /**
     * 求值结果：命中与否 + 判定依据（记入 dynamic_linkage_log.condition_trace）
     */
    public record ConditionResult(boolean pass, String trace) {
    }

    public static ConditionResult evaluate(JSONObject tree, Map<String, Object> data) {
        if (tree == null || tree.isEmpty()) {
            return new ConditionResult(true, "无条件树=恒真");
        }
        List<String> trace = new ArrayList<>();
        boolean pass = evalNode(tree, data, 0, trace);
        return new ConditionResult(pass, String.join("；", trace));
    }

    private static boolean evalNode(JSONObject node, Map<String, Object> data, int depth, List<String> trace) {
        if (depth > MAX_DEPTH) {
            trace.add("条件树超过最大深度" + MAX_DEPTH + "，按不命中处理");
            return false;
        }
        String op = node.getString("op");
        if ("AND".equalsIgnoreCase(op) || "OR".equalsIgnoreCase(op)) {
            JSONArray children = node.getJSONArray("children");
            if (children == null || children.isEmpty()) {
                trace.add(op + "(空组合)=恒真");
                return true;
            }
            List<String> childTraces = new ArrayList<>();
            boolean result = "AND".equalsIgnoreCase(op);
            for (int i = 0; i < children.size(); i++) {
                List<String> subTrace = new ArrayList<>();
                boolean childPass = evalNode(children.getJSONObject(i), data, depth + 1, subTrace);
                childTraces.add("[" + (childPass ? "√" : "×") + "]" + String.join("/", subTrace));
                if ("AND".equalsIgnoreCase(op)) {
                    result = result && childPass;
                } else {
                    result = result || childPass;
                }
            }
            trace.add(op + "(" + childTraces + ")=" + (result ? "命中" : "未命中"));
            return result;
        }
        return evalLeaf(node, data, trace);
    }

    private static boolean evalLeaf(JSONObject leaf, Map<String, Object> data, List<String> trace) {
        String field = leaf.getString("field");
        String cmp = leaf.getString("cmp");
        Object expect = resolveValue(leaf.get("value"), data);
        Object actual = data == null ? null : lookup(data, field);

        boolean pass;
        String detail;
        if ("IS_EMPTY".equals(cmp)) {
            pass = isEmpty(actual);
            detail = field + " 为空=" + pass;
        } else if ("NOT_EMPTY".equals(cmp)) {
            pass = !isEmpty(actual);
            detail = field + " 非空=" + pass;
        } else if (isEmpty(actual)) {
            // 空值与其余比较符一律不命中（IS_EMPTY 语义口径，§8.2 软失败）
            pass = false;
            detail = field + " 无值，" + cmp + " 不命中";
        } else {
            switch (cmp == null ? "" : cmp) {
                case "EQ" -> {
                    pass = compareEquals(actual, expect);
                    detail = field + "(" + actual + " EQ " + expect + ")=" + pass;
                }
                case "NE" -> {
                    pass = !compareEquals(actual, expect);
                    detail = field + "(" + actual + " NE " + expect + ")=" + pass;
                }
                case "GT", "GTE", "LT", "LTE" -> {
                    int c = compareNumberOrText(actual, expect);
                    pass = switch (cmp) {
                        case "GT" -> c > 0;
                        case "GTE" -> c >= 0;
                        case "LT" -> c < 0;
                        default -> c <= 0;
                    };
                    detail = field + "(" + actual + " " + cmp + " " + expect + ")=" + pass;
                }
                case "IN", "NOT_IN" -> {
                    boolean in = false;
                    if (expect instanceof Collection<?> collection) {
                        for (Object item : collection) {
                            if (compareEquals(actual, item)) {
                                in = true;
                                break;
                            }
                        }
                    } else if (expect != null) {
                        in = compareEquals(actual, expect);
                    }
                    pass = "IN".equals(cmp) == in;
                    detail = field + "(" + actual + " " + cmp + " " + expect + ")=" + pass;
                }
                case "CONTAINS" -> {
                    pass = String.valueOf(actual).contains(String.valueOf(expect == null ? "" : expect));
                    detail = field + "(" + actual + " 含 " + expect + ")=" + pass;
                }
                case "STARTS_WITH" -> {
                    pass = String.valueOf(actual).startsWith(String.valueOf(expect == null ? "" : expect));
                    detail = field + "(" + actual + " 前缀 " + expect + ")=" + pass;
                }
                default -> {
                    pass = false;
                    detail = field + " 未知比较符 " + cmp + "，按不命中处理";
                }
            }
        }
        trace.add(detail);
        return pass;
    }

    /**
     * 字段取值：精确键 → 下划线 → 驼峰，容错提交口径差异
     */
    public static Object lookup(Map<String, Object> data, String field) {
        if (data == null || field == null) {
            return null;
        }
        if (data.containsKey(field)) {
            return data.get(field);
        }
        String snake = field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
        if (data.containsKey(snake)) {
            return data.get(snake);
        }
        String camel = toCamel(field);
        return data.get(camel);
    }

    /**
     * 返回 {@link #lookup} 实际命中的键名（容错后的真实列名），
     * 供敏感列匹配等需要"拿回真实键"的场景；未命中返回 null。
     */
    public static String lookupKey(Map<String, Object> data, String field) {
        if (data == null || field == null) {
            return null;
        }
        if (data.containsKey(field)) {
            return field;
        }
        String snake = field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
        if (data.containsKey(snake)) {
            return snake;
        }
        String camel = toCamel(field);
        return data.containsKey(camel) ? camel : null;
    }

    private static String toCamel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    /**
     * value 三种来源（§7.3）：常量 / {@code {{field}}} 事件数据引用 /
     * {@code {{currentUser}}、{{currentDept}}、{{now}}} 上下文变量
     */
    static Object resolveValue(Object value, Map<String, Object> data) {
        if (!(value instanceof String text)) {
            return value;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
            String ref = trimmed.substring(2, trimmed.length() - 2).trim();
            return switch (ref) {
                case "currentUser" -> SecurityUtils.getUserId();
                case "currentDept" -> SecurityUtils.getDepartmentId();
                case "now" -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                default -> data == null ? null : lookup(data, ref);
            };
        }
        return value;
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isEmpty();
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }
        return false;
    }

    /**
     * 值相等判定（数值/文本双口径），供 watchFields 前后对比复用
     */
    public static boolean valuesEqual(Object actual, Object expect) {
        return compareEquals(actual, expect);
    }

    private static boolean compareEquals(Object actual, Object expect) {
        if (actual == null || expect == null) {
            return actual == expect;
        }
        if (actual.equals(expect)) {
            return true;
        }
        BigDecimal a = toNumber(actual);
        BigDecimal b = toNumber(expect);
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private static int compareNumberOrText(Object actual, Object expect) {
        BigDecimal a = toNumber(actual);
        BigDecimal b = toNumber(expect);
        if (a != null && b != null) {
            return a.compareTo(b);
        }
        return String.valueOf(actual).compareTo(String.valueOf(expect));
    }

    private static BigDecimal toNumber(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
