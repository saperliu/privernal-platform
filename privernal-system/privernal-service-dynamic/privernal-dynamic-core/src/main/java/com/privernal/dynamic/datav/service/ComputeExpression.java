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
import com.privernal.common.core.utils.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 派生列（计算字段，M4.2）：受限表达式 → 同一棵 AST 分别翻译成 SQL 与内存求值。
 * <p>
 * <b>为什么不是 Groovy</b>：求值位置有两条路径——动态表在库里（可能百万行，拉进内存会破坏
 * LIMIT 与超时约束），外部源在内存里。Groovy 只能在内存跑，于是动态表要么退化成全表物化、
 * 要么两条路径给出不同结果。受限表达式可以翻译成 SQL 下推，同时用同一棵 AST 在内存侧求值，
 * 一致性由「同一棵树」保证；顺带消掉了「执行用户提交的代码」这一整个攻击面（无 IO、无反射、
 * 无类加载，因为根本没有解释器可以绕）。
 * <p>
 * 语法：
 * <ul>
 *   <li>字段引用 {@code [field_code]}（必须存在于列快照）</li>
 *   <li>字面量 {@code 123.45} / {@code '文本'}</li>
 *   <li>算术 {@code + - * /} 与括号；比较 {@code = != > >= < <=}；逻辑 {@code && ||}</li>
 *   <li>函数 {@code IF(c,a,b) COALESCE ABS ROUND FLOOR CEIL CONCAT UPPER LOWER TRIM LENGTH
 *       YEAR MONTH DAY HOUR MINUTE ISBLANK}</li>
 * </ul>
 *
 * @author saperliu
 */
public final class ComputeExpression {

    /** 允许的函数名（大小写不敏感） */
    private static final Set<String> FUNCTIONS = Set.of(
            "if", "coalesce", "abs", "round", "floor", "ceil", "concat", "upper", "lower",
            "trim", "length", "year", "month", "day", "hour", "minute", "isblank");

    /** 表达式长度上限：解析是递归的，长度就是天然的资源上限 */
    public static final int MAX_LENGTH = 500;

    private ComputeExpression() {
    }

    /**
     * 解析成 AST。
     *
     * @param columnTypes 列快照的 fieldCode→dataType，字段引用必须命中它
     */
    public static Node parse(String expression, Map<String, String> columnTypes) {
        if (StringUtils.isBlank(expression)) {
            throw new ServiceException("计算字段表达式不能为空");
        }
        if (expression.length() > MAX_LENGTH) {
            throw new ServiceException("计算字段表达式最长 " + MAX_LENGTH + " 字符");
        }
        Parser parser = new Parser(expression, columnTypes);
        Node node = parser.parseExpression();
        parser.expectEnd();
        return node;
    }

    /** AST 节点 */
    public interface Node {
        String toSql(Function<String, String> columnOf);

        Object evaluate(Map<String, Object> row);
    }

    // ==================== 词法 + 递归下降解析 ====================

    private static final class Parser {
        private final String text;
        private final Map<String, String> columnTypes;
        private int pos;

        Parser(String text, Map<String, String> columnTypes) {
            this.text = text;
            this.columnTypes = columnTypes;
        }

        Node parseExpression() {
            return parseOr();
        }

        void expectEnd() {
            skipSpaces();
            if (pos < text.length()) {
                throw new ServiceException("计算字段表达式在「" + text.substring(pos) + "」处无法解析");
            }
        }

        private Node parseOr() {
            Node left = parseAnd();
            while (consume("||")) {
                left = new Binary("||", left, parseAnd());
            }
            return left;
        }

        private Node parseAnd() {
            Node left = parseComparison();
            while (consume("&&")) {
                left = new Binary("&&", left, parseComparison());
            }
            return left;
        }

        private Node parseComparison() {
            Node left = parseAdditive();
            for (String op : new String[]{"<=", ">=", "!=", "<>", "=", "<", ">"}) {
                if (consume(op)) {
                    return new Binary(op, left, parseAdditive());
                }
            }
            return left;
        }

        private Node parseAdditive() {
            Node left = parseMultiplicative();
            while (true) {
                if (consume("+")) {
                    left = new Binary("+", left, parseMultiplicative());
                } else if (consume("-")) {
                    left = new Binary("-", left, parseMultiplicative());
                } else {
                    return left;
                }
            }
        }

        private Node parseMultiplicative() {
            Node left = parseUnary();
            while (true) {
                if (consume("*")) {
                    left = new Binary("*", left, parseUnary());
                } else if (consume("/")) {
                    left = new Binary("/", left, parseUnary());
                } else {
                    return left;
                }
            }
        }

        private Node parseUnary() {
            if (consume("!")) {
                return new Unary("!", parseUnary());
            }
            if (consume("-")) {
                return new Unary("-", parseUnary());
            }
            return parsePrimary();
        }

        private Node parsePrimary() {
            skipSpaces();
            if (pos >= text.length()) {
                throw new ServiceException("计算字段表达式不完整");
            }
            char ch = text.charAt(pos);
            if (ch == '(') {
                pos++;
                Node inner = parseExpression();
                skipSpaces();
                if (pos >= text.length() || text.charAt(pos) != ')') {
                    throw new ServiceException("计算字段表达式缺少右括号");
                }
                pos++;
                return inner;
            }
            if (ch == '[') {
                int end = text.indexOf(']', pos);
                if (end < 0) {
                    throw new ServiceException("字段引用缺少 ]");
                }
                String field = text.substring(pos + 1, end).trim();
                pos = end + 1;
                if (!columnTypes.containsKey(field)) {
                    throw new ServiceException("计算字段引用的列不存在于数据集列快照：" + field);
                }
                return new FieldRef(field);
            }
            if (ch == '\'' || ch == '"') {
                char quote = ch;
                StringBuilder value = new StringBuilder();
                pos++;
                boolean closed = false;
                while (pos < text.length()) {
                    char current = text.charAt(pos++);
                    // 反斜杠转义：不支持它的话，「O'Brien」这种档位名根本写不出来，
                    // 而 SQL 侧的 '' 转义又要求这里能表达出单个引号
                    if (current == '\\' && pos < text.length()) {
                        char next = text.charAt(pos++);
                        value.append(next == 'n' ? '\n' : next == 't' ? '\t' : next);
                        continue;
                    }
                    if (current == quote) {
                        closed = true;
                        break;
                    }
                    value.append(current);
                }
                if (!closed) {
                    throw new ServiceException("字符串字面量缺少收尾引号");
                }
                return new Literal(value.toString());
            }
            if (Character.isDigit(ch) || ch == '.') {
                int start = pos;
                while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                    pos++;
                }
                String number = text.substring(start, pos);
                try {
                    return new Literal(new BigDecimal(number));
                } catch (NumberFormatException e) {
                    throw new ServiceException("非法数字字面量：" + number);
                }
            }
            if (Character.isLetter(ch) || ch == '_') {
                int start = pos;
                while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
                    pos++;
                }
                String word = text.substring(start, pos);
                String lower = word.toLowerCase();
                if ("true".equals(lower) || "false".equals(lower)) {
                    return new Literal(Boolean.valueOf(lower));
                }
                if ("null".equals(lower)) {
                    return new Literal(null);
                }
                if (!FUNCTIONS.contains(lower)) {
                    throw new ServiceException("计算字段不支持的函数或关键字：" + word);
                }
                skipSpaces();
                if (pos >= text.length() || text.charAt(pos) != '(') {
                    throw new ServiceException(word + " 后面必须跟 (");
                }
                pos++;
                List<Node> args = new ArrayList<>();
                skipSpaces();
                if (pos < text.length() && text.charAt(pos) == ')') {
                    pos++;
                } else {
                    do {
                        args.add(parseExpression());
                        skipSpaces();
                    } while (consume(","));
                    skipSpaces();
                    if (pos >= text.length() || text.charAt(pos) != ')') {
                        throw new ServiceException(word + " 缺少右括号");
                    }
                    pos++;
                }
                return new Call(lower, args);
            }
            throw new ServiceException("计算字段表达式在「" + wordAround(pos) + "」处无法解析");
        }

        private String wordAround(int index) {
            int end = Math.min(text.length(), index + 12);
            return text.substring(index, end);
        }

        private void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private boolean consume(String token) {
            skipSpaces();
            if (text.startsWith(token, pos)) {
                // 避免把 `a=-1` 里的 `=` 之后又当成比较，也避免 `<=` 被拆成 `<` + `=`
                pos += token.length();
                return true;
            }
            return false;
        }
    }

    // ==================== AST 节点 ====================

    private record Literal(Object value) implements Node {
        @Override
        public String toSql(Function<String, String> columnOf) {
            if (value == null) {
                return "NULL";
            }
            if (value instanceof BigDecimal number) {
                return number.toPlainString();
            }
            if (value instanceof Boolean flag) {
                return flag ? "1" : "0";
            }
            // 单引号翻倍：一个带引号的字符串常量不能改变 SQL 的结构
            return "'" + String.valueOf(value).replace("'", "''") + "'";
        }

        @Override
        public Object evaluate(Map<String, Object> row) {
            return value;
        }
    }

    private record FieldRef(String field) implements Node {
        @Override
        public String toSql(Function<String, String> columnOf) {
            return columnOf.apply(field);
        }

        @Override
        public Object evaluate(Map<String, Object> row) {
            return row.get(field);
        }
    }

    private record Unary(String op, Node child) implements Node {
        @Override
        public String toSql(Function<String, String> columnOf) {
            return "!".equals(op) ? "(NOT (" + child.toSql(columnOf) + "))" : "(-(" + child.toSql(columnOf) + "))";
        }

        @Override
        public Object evaluate(Map<String, Object> row) {
            Object value = child.evaluate(row);
            if ("!".equals(op)) {
                return !truthy(value);
            }
            BigDecimal number = decimal(value);
            return number == null ? null : number.negate();
        }
    }

    private record Binary(String op, Node left, Node right) implements Node {
        @Override
        public String toSql(Function<String, String> columnOf) {
            String sqlOp = switch (op) {
                case "!=" -> "<>";
                case "&&" -> " AND ";
                case "||" -> " OR ";
                default -> op;
            };
            return "(" + left.toSql(columnOf) + sqlOp + right.toSql(columnOf) + ")";
        }

        @Override
        public Object evaluate(Map<String, Object> row) {
            Object a = left.evaluate(row);
            Object b = right.evaluate(row);
            switch (op) {
                case "&&":
                    return truthy(a) && truthy(b);
                case "||":
                    return truthy(a) || truthy(b);
                case "+", "-", "*", "/": {
                    BigDecimal x = decimal(a);
                    BigDecimal y = decimal(b);
                    if (x == null || y == null) {
                        return null;
                    }
                    return switch (op) {
                        case "+" -> x.add(y);
                        case "-" -> x.subtract(y);
                        case "*" -> x.multiply(y);
                        // 除不尽时保留 10 位，与 MySQL 的默认精度量级一致
                        default -> y.compareTo(BigDecimal.ZERO) == 0
                                ? null : x.divide(y, 10, RoundingMode.HALF_UP).stripTrailingZeros();
                    };
                }
                default: {
                    int cmp = compare(a, b);
                    return switch (op) {
                        case "=" -> cmp == 0;
                        case "!=", "<>" -> cmp != 0;
                        case ">" -> cmp > 0;
                        case ">=" -> cmp >= 0;
                        case "<" -> cmp < 0;
                        default -> cmp <= 0;
                    };
                }
            }
        }
    }

    private record Call(String name, List<Node> args) implements Node {
        @Override
        public String toSql(Function<String, String> columnOf) {
            List<String> sqlArgs = args.stream().map(arg -> arg.toSql(columnOf)).toList();
            String joined = String.join(", ", sqlArgs);
            return switch (name) {
                case "if" -> "(CASE WHEN " + sqlArgs.get(0) + " THEN " + sqlArgs.get(1)
                        + " ELSE " + (sqlArgs.size() > 2 ? sqlArgs.get(2) : "NULL") + " END)";
                case "isblank" -> "(" + sqlArgs.get(0) + " IS NULL OR " + sqlArgs.get(0) + " = '')";
                // LENGTH 在 MySQL/SQLite 均可用（MySQL 按字节、SQLite 按字符，多字节串语义有差但公式场景够用）；
                // 原 CHAR_LENGTH 是 MySQL 独有，SQLite 下直接报 no such function
                case "length" -> "LENGTH(" + sqlArgs.get(0) + ")";
                // MySQL 的 CEIL 与 Java 的 Math.ceil 命名不同，这里按目标库改名
                case "ceil" -> "CEIL(" + joined + ")";
                case "concat" -> "CONCAT(" + joined + ")";
                default -> name.toUpperCase() + "(" + joined + ")";
            };
        }

        @Override
        public Object evaluate(Map<String, Object> row) {
            List<Object> values = new ArrayList<>(args.size());
            // IF 与 COALESCE 要短路：其余函数先算完参数
            if ("if".equals(name)) {
                boolean pass = truthy(args.get(0).evaluate(row));
                return pass ? args.get(1).evaluate(row) : (args.size() > 2 ? args.get(2).evaluate(row) : null);
            }
            if ("coalesce".equals(name)) {
                for (Node arg : args) {
                    Object value = arg.evaluate(row);
                    if (value != null) {
                        return value;
                    }
                }
                return null;
            }
            for (Node arg : args) {
                values.add(arg.evaluate(row));
            }
            return apply(values);
        }

        private Object apply(List<Object> values) {
            switch (name) {
                case "abs": {
                    BigDecimal v = decimal(values.get(0));
                    return v == null ? null : v.abs();
                }
                case "round": {
                    BigDecimal v = decimal(values.get(0));
                    if (v == null) {
                        return null;
                    }
                    int digits = values.size() > 1 && decimal(values.get(1)) != null
                            ? decimal(values.get(1)).intValue() : 0;
                    return v.setScale(digits, RoundingMode.HALF_UP);
                }
                case "floor": {
                    BigDecimal v = decimal(values.get(0));
                    return v == null ? null : v.setScale(0, RoundingMode.FLOOR);
                }
                case "ceil": {
                    BigDecimal v = decimal(values.get(0));
                    return v == null ? null : v.setScale(0, RoundingMode.CEILING);
                }
                case "concat": {
                    StringBuilder text = new StringBuilder();
                    for (Object value : values) {
                        text.append(value == null ? "" : plain(value));
                    }
                    return text.toString();
                }
                case "upper":
                    return values.get(0) == null ? null : String.valueOf(values.get(0)).toUpperCase();
                case "lower":
                    return values.get(0) == null ? null : String.valueOf(values.get(0)).toLowerCase();
                case "trim":
                    return values.get(0) == null ? null : String.valueOf(values.get(0)).trim();
                case "length":
                    return values.get(0) == null ? null : (long) String.valueOf(values.get(0)).length();
                case "isblank":
                    Object v = values.get(0);
                    return v == null || "".equals(String.valueOf(v).trim());
                case "year":
                case "month":
                case "day":
                case "hour":
                case "minute":
                    return datePart(name, values.get(0));
                default:
                    throw new ServiceException("计算字段不支持的函数：" + name);
            }
        }
    }

    // ==================== 值语义 ====================

    /** 时间部分：外部源给字符串、动态表给 java.time，两种都要能取到 */
    private static Object datePart(String name, Object raw) {
        if (raw == null) {
            return null;
        }
        java.time.temporal.TemporalAccessor moment = null;
        if (raw instanceof LocalDateTime dateTime) {
            moment = dateTime;
        } else if (raw instanceof LocalDate date) {
            moment = date;
        } else {
            String text = String.valueOf(raw).trim();
            try {
                // 只取日期部分即可满足 YEAR/MONTH/DAY；HOUR/MINUTE 需要时间部分
                String head = text.length() >= 19 ? text.substring(0, 19) : text;
                moment = LocalDateTime.parse(head.replace(' ', 'T'));
            } catch (Exception e) {
                try {
                    moment = LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        if (moment instanceof LocalDateTime dateTime) {
            return switch (name) {
                case "year" -> (long) dateTime.getYear();
                case "month" -> (long) dateTime.getMonthValue();
                case "day" -> (long) dateTime.getDayOfMonth();
                case "hour" -> (long) dateTime.getHour();
                default -> (long) dateTime.getMinute();
            };
        }
        LocalDate date = (LocalDate) moment;
        return switch (name) {
            case "year" -> (long) date.getYear();
            case "month" -> (long) date.getMonthValue();
            default -> (long) date.getDayOfMonth();
        };
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal number) {
            return number;
        }
        if (raw instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        if (raw instanceof Boolean flag) {
            return flag ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean truthy(Object raw) {
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean flag) {
            return flag;
        }
        BigDecimal number = decimal(raw);
        if (number != null) {
            return number.compareTo(BigDecimal.ZERO) != 0;
        }
        String text = String.valueOf(raw);
        return !text.isEmpty() && !"false".equalsIgnoreCase(text);
    }

    /**
     * 比较。两侧一致性的关键：MySQL 默认排序规则（collation）对字符串大小写不敏感，
     * 所以内存侧也必须忽略大小写比较，否则 `IF([name]='Tom', ...)` 在动态表上成立、
     * 在外部源上可能不成立。数值一律按 BigDecimal 比（1.0 与 1.00 相等）。
     */
    private static int compare(Object left, Object right) {
        BigDecimal a = decimal(left);
        BigDecimal b = decimal(right);
        if (a != null && b != null) {
            return a.compareTo(b);
        }
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
    }

    /** 数字转字符串时去掉 BigDecimal 的尾零，否则 CONCAT 会输出 1.50 而 SQL 侧输出 1.5 */
    private static String plain(Object value) {
        if (value instanceof BigDecimal number) {
            return number.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    /** 供调用方复用的 Set，避免各处再抄一份函数白名单 */
    public static Set<String> supportedFunctions() {
        return FUNCTIONS;
    }
}
