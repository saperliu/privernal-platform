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

package com.privernal.dynamic.dbs.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态表查询条件片段的安全翻译器（S1 修复）。
 *
 * <p>历史实现把请求里的条件字符串经 MyBatis {@code ${}} 原样拼进 WHERE，
 * 匿名分享端点 {@code /pub/view/list/{token}} 因此存在 SQL 注入。
 * 本类把条件片段<b>先解析、再重建</b>：字段经白名单校验后反引号拼接，
 * 值一律改写为 {@code #{condParams.pN}} 预编译绑定——语法之外的一切输入直接拒绝，
 * 不做任何"净化后放行"的尝试。</p>
 *
 * <p>接受的语法与生产端 {@code FormTransferTable#dataGetSQL} 的输出严格对齐
 * （列名可有可无反引号、可有前导空白），并保留 API 层面的链式能力：</p>
 * <pre>
 *   comparison ( AND comparison )*          —— 链分隔符仅认大写 AND
 *   comparison := column (= | &lt;&gt; | != | &gt;= | &lt;= | &gt; | &lt;) literal
 *              |  column LIKE literal
 *              |  column [NOT] IN (literal [, literal]*)
 *              |  column [NOT] BETWEEN literal AND literal   —— 内部 and 大小写均可
 *              |  column IS [NOT] NULL
 *   column     := [`]ident[`]     （ident = 字母开头的字母/数字/下划线/$）
 *   literal    := 'string'（'' 转义单引号） | 整数/小数
 * </pre>
 *
 * <p>语法之外的一切输入（子查询、注释、函数、分号、小写 and 链等）抛出
 * {@link IllegalArgumentException}，由全局异常处理映射为 400。</p>
 */
public final class SafeConditionFragment {

    private static final Pattern COLUMN = Pattern.compile("`?([A-Za-z][A-Za-z0-9_$]*)`?");

    private static final Pattern IS_NULL = Pattern.compile("IS\\s+(NOT\\s+)?NULL", Pattern.CASE_INSENSITIVE);

    private static final Pattern NOT_IN = Pattern.compile("NOT\\s+IN\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern IN = Pattern.compile("IN\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern NOT_BETWEEN = Pattern.compile("NOT\\s+BETWEEN\\s+", Pattern.CASE_INSENSITIVE);

    private static final Pattern BETWEEN = Pattern.compile("BETWEEN\\s+", Pattern.CASE_INSENSITIVE);

    private static final Pattern LIKE = Pattern.compile("LIKE", Pattern.CASE_INSENSITIVE);

    private static final Pattern COMPARATOR = Pattern.compile("<>|!=|>=|<=|=|>|<");

    private static final Pattern LITERAL = Pattern.compile("'(?:[^']|'')*'|\\d+(?:\\.\\d+)?");

    /** 单次查询绑定参数上限：防御畸形大条件集 */
    private static final int MAX_PARAMS = 200;

    private SafeConditionFragment() {
    }

    /**
     * 解析并重建一个条件片段。
     *
     * @param fragment 请求带来的条件 SQL 片段，如 {@code `name` LIKE '%张%'}
     * @return [0]=重建后的安全片段（含 {@code #{condParams.pN}} 占位），[1]=绑定参数 Map
     * @throws IllegalArgumentException 语法不允许的输入（含一切注入形态）
     */
    public static Object[] parse(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            throw new IllegalArgumentException("查询条件片段为空");
        }
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        int[] counter = {0};
        int pos = 0;
        int len = fragment.length();
        boolean first = true;
        while (true) {
            pos = skipSpaces(fragment, pos);
            if (pos >= len) {
                throw new IllegalArgumentException("查询条件片段语法不完整");
            }
            if (!first) {
                if (!fragment.startsWith("AND", pos)) {
                    throw new IllegalArgumentException("条件链分隔符只支持大写 AND");
                }
                sql.append(" AND ");
                pos = skipSpaces(fragment, pos + "AND".length());
            }
            pos = parseComparison(fragment, pos, sql, params, counter);
            first = false;
            pos = skipSpaces(fragment, pos);
            if (pos >= len) {
                return new Object[]{sql.toString(), params};
            }
        }
    }

    /** 在 pos 处解析一个比较式，返回新的 pos；解析失败抛出 IllegalArgumentException */
    private static int parseComparison(String s, int pos, StringBuilder sql,
                                       Map<String, Object> params, int[] counter) {
        Matcher columnMatcher = COLUMN.matcher(s);
        columnMatcher.region(pos, s.length());
        if (!columnMatcher.lookingAt()) {
            throw new IllegalArgumentException("条件字段缺失或非法：" + preview(s.substring(pos)));
        }
        String column = "`" + columnMatcher.group(1) + "`";
        pos = skipSpaces(s, columnMatcher.end());

        Matcher matcher = IS_NULL.matcher(s);
        matcher.region(pos, s.length());
        if (matcher.lookingAt()) {
            sql.append(column).append(" IS ").append(matcher.group(1) == null ? "" : "NOT ").append("NULL");
            return matcher.end();
        }

        boolean not;
        matcher = NOT_BETWEEN.matcher(s);
        matcher.region(pos, s.length());
        if (matcher.lookingAt()) {
            not = true;
            pos = matcher.end();
        } else {
            matcher = BETWEEN.matcher(s);
            matcher.region(pos, s.length());
            if (matcher.lookingAt()) {
                not = false;
                pos = matcher.end();
            } else {
                not = false;
                matcher = null;
            }
        }
        if (matcher != null) {
            LiteralResult lower = literal(s, pos, params, counter);
            int afterLower = skipSpaces(s, lower.pos);
            // BETWEEN 内部的 and 大小写均可（生产端输出即小写）；它与链分隔符无歧义——结构固定为 值 AND 值
            if (!s.regionMatches(afterLower, "AND", 0, 3) && !s.regionMatches(afterLower, "and", 0, 3)) {
                throw new IllegalArgumentException("BETWEEN 缺少 AND 上界：" + preview(s.substring(afterLower)));
            }
            LiteralResult upper = literal(s, skipSpaces(s, afterLower + 3), params, counter);
            sql.append(column).append(not ? " NOT BETWEEN " : " BETWEEN ")
                    .append(lower.value).append(" AND ").append(upper.value);
            return upper.pos;
        }

        boolean inNot;
        matcher = NOT_IN.matcher(s);
        matcher.region(pos, s.length());
        if (matcher.lookingAt()) {
            inNot = true;
            pos = matcher.end();
        } else {
            matcher = IN.matcher(s);
            matcher.region(pos, s.length());
            if (matcher.lookingAt()) {
                inNot = false;
                pos = matcher.end();
            } else {
                inNot = false;
                matcher = null;
            }
        }
        if (matcher != null) {
            StringBuilder in = new StringBuilder(column).append(inNot ? " NOT IN (" : " IN (");
            boolean firstItem = true;
            while (true) {
                LiteralResult item = literal(s, skipSpaces(s, pos), params, counter);
                if (!firstItem) {
                    in.append(", ");
                }
                in.append(item.value);
                pos = skipSpaces(s, item.pos);
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                    firstItem = false;
                    continue;
                }
                if (pos >= s.length() || s.charAt(pos) != ')') {
                    throw new IllegalArgumentException("IN 列表未闭合：" + preview(s.substring(pos)));
                }
                pos++;
                break;
            }
            sql.append(in).append(")");
            return pos;
        }

        matcher = LIKE.matcher(s);
        matcher.region(pos, s.length());
        if (matcher.lookingAt()) {
            pos = matcher.end();
            LiteralResult value = literal(s, skipSpaces(s, pos), params, counter);
            sql.append(column).append(" LIKE ").append(value.value);
            return value.pos;
        }

        matcher = COMPARATOR.matcher(s);
        matcher.region(pos, s.length());
        if (matcher.lookingAt()) {
            pos = matcher.end();
            LiteralResult value = literal(s, skipSpaces(s, pos), params, counter);
            sql.append(column).append(" ").append(matcher.group()).append(" ").append(value.value);
            return value.pos;
        }

        throw new IllegalArgumentException("条件运算符不支持：" + preview(s.substring(pos)));
    }

    /** 解析结果：重建后的占位串与结束位置 */
    private static final class LiteralResult {
        final String value;
        final int pos;

        LiteralResult(String value, int pos) {
            this.value = value;
            this.pos = pos;
        }
    }

    /** 解析一个字面量并绑定为参数；返回重建后的占位与结束位置 */
    private static LiteralResult literal(String s, int pos, Map<String, Object> params, int[] counter) {
        if (counter[0] >= MAX_PARAMS) {
            throw new IllegalArgumentException("查询条件参数超过上限 " + MAX_PARAMS);
        }
        Matcher m = LITERAL.matcher(s);
        m.region(pos, s.length());
        if (!m.lookingAt()) {
            throw new IllegalArgumentException("条件值必须是引号字符串或数字：" + preview(s.substring(pos)));
        }
        String name = "p" + (++counter[0]);
        String raw = m.group();
        Object value;
        if (raw.startsWith("'")) {
            value = raw.substring(1, raw.length() - 1).replace("''", "'");
        } else if (raw.contains(".")) {
            value = new java.math.BigDecimal(raw);
        } else {
            value = Long.valueOf(raw);
        }
        params.put(name, value);
        return new LiteralResult("#{condParams." + name + "}", m.end());
    }

    private static int skipSpaces(String s, int pos) {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private static String preview(String s) {
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > 40 ? flat.substring(0, 40) + "…" : flat;
    }
}
