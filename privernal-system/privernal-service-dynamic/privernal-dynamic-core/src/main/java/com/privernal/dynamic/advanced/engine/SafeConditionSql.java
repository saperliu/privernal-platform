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

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 条件树 → 安全 WHERE 片段（§7.4 DATA_UPDATE/DATA_DELETE 目标行定位）。
 * 标识符经正则白名单校验后以反引号拼接，值一律 {@code #{params.pN}} 预编译绑定，杜绝自由 SQL。
 * 与 {@link ConditionEvaluator} 同一套操作符子集（可进 SQL 的部分），组合结构 AND/OR 同构翻译。
 * <p>
 * 约定：调用方负责把 {@code deleted_flag='N'} 语义加上（本类只翻译业务条件），
 * 并负责把返回的 fragment 与 params 一起交给 {@code DynamicLinkageQueryMapper.selectRows}。
 */
public final class SafeConditionSql {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    /** 单条语句绑定参数上限：防御畸形大条件树 */
    private static final int MAX_PARAMS = 200;

    private SafeConditionSql() {
    }

    public static boolean isValidIdentifier(String name) {
        return name != null && IDENTIFIER.matcher(name).matches();
    }

    /**
     * @return [0]=where 片段（不带 WHERE 关键字，可为空串），[1]=参数 Map（p1..pN）
     */
    public static Object[] build(JSONObject tree) {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new java.util.HashMap<>();
        int[] counter = {0};
        // null/空树=无条件（DATA_UPDATE 允许留空=作用于全部数据），返回空 WHERE 由调用方兜底 1=1
        if (tree != null && !tree.isEmpty()) {
            appendNode(tree, sql, params, counter, 0);
        }
        return new Object[]{sql.toString(), params};
    }

    private static void appendNode(JSONObject node, StringBuilder sql, Map<String, Object> params,
                                   int[] counter, int depth) {
        if (depth > ConditionEvaluator.MAX_DEPTH) {
            throw new IllegalArgumentException("条件树超过最大深度");
        }
        String op = node.getString("op");
        if ("AND".equalsIgnoreCase(op) || "OR".equalsIgnoreCase(op)) {
            JSONArray children = node.getJSONArray("children");
            if (children == null || children.isEmpty()) {
                sql.append("1=1");
                return;
            }
            sql.append("(");
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    sql.append(" ").append(op.toUpperCase()).append(" ");
                }
                appendNode(children.getJSONObject(i), sql, params, counter, depth + 1);
            }
            sql.append(")");
            return;
        }
        appendLeaf(node, sql, params, counter);
    }

    private static void appendLeaf(JSONObject leaf, StringBuilder sql, Map<String, Object> params, int[] counter) {
        String field = leaf.getString("field");
        String cmp = leaf.getString("cmp");
        if (!isValidIdentifier(field)) {
            throw new IllegalArgumentException("非法的条件字段编码：" + field);
        }
        if (counter[0] >= MAX_PARAMS) {
            throw new IllegalArgumentException("条件参数超过上限 " + MAX_PARAMS);
        }
        // 条件字段编码是设计器驼峰口径，落库列名是下划线（与 ConditionEvaluator.lookup 同一容错）
        String columnName = field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
        String column = "`" + columnName + "`";
        switch (cmp == null ? "" : cmp) {
            case "IS_EMPTY" -> sql.append("(").append(column).append(" IS NULL OR ").append(column).append(" = '')");
            case "NOT_EMPTY" -> sql.append("(").append(column).append(" IS NOT NULL AND ").append(column).append(" <> '')");
            case "EQ", "NE", "GT", "GTE", "LT", "LTE" -> {
                String operator = switch (cmp) {
                    case "EQ" -> "=";
                    case "NE" -> "<>";
                    case "GT" -> ">";
                    case "GTE" -> ">=";
                    case "LT" -> "<";
                    default -> "<=";
                };
                String param = bind(params, counter, normalizeBindValue(leaf.get("value")));
                sql.append(column).append(operator).append("#{params.").append(param).append("}");
            }
            case "IN", "NOT_IN" -> {
                List<Object> values = new ArrayList<>();
                Object value = leaf.get("value");
                if (value instanceof List<?> list) {
                    values.addAll(list);
                } else if (value instanceof JSONArray array) {
                    values.addAll(array);
                } else if (value != null) {
                    values.add(value);
                }
                if (values.isEmpty()) {
                    // 空 IN 集合：IN 恒不命中、NOT_IN 恒命中
                    sql.append("IN".equals(cmp) ? "1=0" : "1=1");
                    return;
                }
                if (counter[0] + values.size() > MAX_PARAMS) {
                    throw new IllegalArgumentException("条件参数超过上限 " + MAX_PARAMS);
                }
                sql.append(column).append("IN".equals(cmp) ? " IN (" : " NOT IN (");                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    String param = bind(params, counter, values.get(i));
                    sql.append("#{params.").append(param).append("}");
                }
                sql.append(")");
            }
            case "CONTAINS" -> {
                String param = bind(params, counter, "%" + leaf.getString("value") + "%");
                sql.append(column).append(" LIKE #{params.").append(param).append("}");
            }
            case "STARTS_WITH" -> {
                String param = bind(params, counter, leaf.getString("value") + "%");
                sql.append(column).append(" LIKE #{params.").append(param).append("}");
            }
            default -> throw new IllegalArgumentException("条件不能翻译为 SQL 的比较符：" + cmp);
        }
    }

    private static String bind(Map<String, Object> params, int[] counter, Object value) {
        String name = "p" + (++counter[0]);
        params.put(name, value);
        return name;
    }

    /**
     * {{field}} / {{now}} 引用在 SQL 定位场景不支持（目标行定位按字面值），置 null 走 IS NULL 语义
     */
    private static Object normalizeBindValue(Object value) {
        if (value instanceof String s && s.trim().startsWith("{{")) {
            return null;
        }
        return value;
    }
}
