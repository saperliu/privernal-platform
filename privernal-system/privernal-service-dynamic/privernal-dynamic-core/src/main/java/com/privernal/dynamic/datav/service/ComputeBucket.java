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
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;

/**
 * 数值分桶（M4.1）：把连续度量按显式区间折成维度，例如「100 以下 / 100–500 / 500 以上」。
 * <p>
 * 区间语义在这里定义一次，两条路径共用：
 * <ul>
 *   <li>{@link #labelOf} 内存聚合逐行判定</li>
 *   <li>{@link #caseWhen} SQL 下推生成 {@code CASE WHEN}</li>
 * </ul>
 * 两边各写一份的话，边界值归哪一档迟早会分叉——而分桶图的读者只会看到"某一档数量不对"，
 * 不会想到是 {@code >=} 与 {@code >} 的差别。
 * <p>
 * 语义约定：<b>左闭右开</b>（{@code min <= v < max}），min/max 缺一侧即表示不设下限/上限；
 * 非数值与落在所有区间之外的值进 {@code elseLabel}（未配则原样输出该值，保持与不分桶一致）。
 *
 * @author saperliu
 */
public final class ComputeBucket {

    private ComputeBucket() {
    }

    /** 校验并返回可用的桶定义；没配分桶时返回 null */
    public static JSONObject parse(JSONObject dimension) {
        if (dimension == null) {
            return null;
        }
        JSONObject bucket = dimension.getJSONObject("bucket");
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        JSONArray bins = bucket.getJSONArray("bins");
        if (bins == null || bins.isEmpty()) {
            throw new ServiceException("分桶至少需要一个区间");
        }
        if (bins.size() > 50) {
            throw new ServiceException("分桶区间最多 50 个，当前 " + bins.size() + " 个");
        }
        for (int i = 0; i < bins.size(); i++) {
            JSONObject bin = bins.getJSONObject(i);
            if (bin == null || StringUtils.isBlank(bin.getString("label"))) {
                throw new ServiceException("第 " + (i + 1) + " 个分桶区间缺少标签");
            }
            BigDecimal min = decimal(bin.get("min"));
            BigDecimal max = decimal(bin.get("max"));
            if (min == null && max == null) {
                throw new ServiceException("分桶区间「" + bin.getString("label") + "」至少要给一个边界");
            }
            if (min != null && max != null && min.compareTo(max) >= 0) {
                throw new ServiceException("分桶区间「" + bin.getString("label") + "」的下限必须小于上限");
            }
        }
        return bucket;
    }

    /** 内存路径：一行值落在哪一档 */
    public static String labelOf(Object raw, JSONObject bucket) {
        if (bucket == null) {
            return null;
        }
        BigDecimal value = decimal(raw);
        if (value == null) {
            return elseLabel(bucket);
        }
        JSONArray bins = bucket.getJSONArray("bins");
        for (int i = 0; i < bins.size(); i++) {
            JSONObject bin = bins.getJSONObject(i);
            if (contains(value, decimal(bin.get("min")), decimal(bin.get("max")))) {
                return bin.getString("label");
            }
        }
        return elseLabel(bucket);
    }

    /**
     * SQL 路径：生成 {@code CASE WHEN}。
     * <p>
     * 边界值直接写进 SQL 而不是走参数绑定——它们是配置里的数字，{@link #decimal} 已经把它们
     * 归一成 {@code BigDecimal.toPlainString()}（不可能带引号或运算符），
     * 而 CASE 分支若用参数占位符会让 SQLServer 的类型推断变得不确定。
     */
    public static String caseWhen(String expr, JSONObject bucket) {
        if (bucket == null) {
            return expr;
        }
        StringBuilder sql = new StringBuilder("CASE");
        JSONArray bins = bucket.getJSONArray("bins");
        for (int i = 0; i < bins.size(); i++) {
            JSONObject bin = bins.getJSONObject(i);
            BigDecimal min = decimal(bin.get("min"));
            BigDecimal max = decimal(bin.get("max"));
            sql.append(" WHEN ");
            if (min != null) {
                sql.append(expr).append(" >= ").append(min.toPlainString());
                if (max != null) {
                    sql.append(" AND ");
                }
            }
            if (max != null) {
                sql.append(expr).append(" < ").append(max.toPlainString());
            }
            sql.append(" THEN '").append(escape(bin.getString("label"))).append('\'');
        }
        String elseLabel = elseLabel(bucket);
        if (elseLabel != null) {
            sql.append(" ELSE '").append(escape(elseLabel)).append('\'');
        } else {
            // 没配「其他」标签时，落在区间外的值原样输出：比分桶前多出一档「未知」更容易被误读，
            // 但 NULL 必须转成字符串，否则 CASE 的返回类型会不稳定
            sql.append(" ELSE CAST(").append(expr).append(" AS CHAR)");
        }
        sql.append(" END");
        return sql.toString();
    }

    private static boolean contains(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min != null && value.compareTo(min) < 0) {
            return false;
        }
        return max == null || value.compareTo(max) < 0;
    }

    private static String elseLabel(JSONObject bucket) {
        return StringUtils.trimToNull(bucket.getString("elseLabel"));
    }

    /** 标签里出现单引号要转义，否则一个写着 O'Brien 的档位名就能改掉整条 SQL 的含义 */
    private static String escape(String text) {
        return text == null ? "" : text.replace("'", "''");
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null || "".equals(raw)) {
            return null;
        }
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
