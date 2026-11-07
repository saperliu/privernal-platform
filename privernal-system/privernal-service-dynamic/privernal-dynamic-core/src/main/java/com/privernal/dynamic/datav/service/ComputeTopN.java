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
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * TopN 与「其他」归并（M4.4）。
 * <p>
 * 做成两条路径共用的**后处理**而不是各自实现：SQL 侧靠 {@code ORDER BY ... LIMIT n} 截断，
 * 内存侧靠排序后 subList，看起来都能做；但一旦要归并「其他」，两边就得重算一次聚合，
 * 而重算的输入（分组结果）在两条路径上形状不同——最后必然是同一份配置给出两个"其他"值。
 * 放在结果行上做，两侧共用同一段代码，一致性是结构上保证的，不靠测试兜。
 * <p>
 * 代价是必须先把全部分组取回来（受 {@code MAX_ROWS} 保护），所以 {@code LIMIT} 不能提前截断，
 * 否则「其他」只汇总了被 LIMIT 砍剩的那几组，数字看着对、其实是错的。
 *
 * @author saperliu
 */
public final class ComputeTopN {

    /** 归并「其他」时用来加权求平均的隐藏列，返回给调用方前会被剥掉 */
    public static final String WEIGHT_ALIAS = "__n";

    private ComputeTopN() {
    }

    /**
     * @param rows      聚合结果行（含维度别名与度量别名两列）
     * @param topN      {@code {by, n, order, mergeRestAs}}；null 或 n<=0 时原样返回
     * @param aliasAgg  度量别名 → 聚合函数，用于判断能不能安全合并
     * @param dimAlias  「其他」行要写回的维度别名（第一个维度）
     */
    public static void apply(List<JSONObject> rows, JSONObject topN, Map<String, String> aliasAgg, String dimAlias) {
        if (topN == null || rows == null || rows.isEmpty()) {
            return;
        }
        int n = topN.getIntValue("n");
        if (n <= 0) {
            return;
        }
        String by = StringUtils.defaultIfBlank(topN.getString("by"), aliasAgg.keySet().iterator().next());
        boolean ascending = "asc".equalsIgnoreCase(StringUtils.defaultIfBlank(topN.getString("order"), "desc"));
        String mergeRestAs = StringUtils.trimToNull(topN.getString("mergeRestAs"));

        rows.sort((left, right) -> {
            BigDecimal a = decimal(left.get(by));
            BigDecimal b = decimal(right.get(by));
            if (a == null && b == null) {
                return 0;
            }
            // 空值排在最后：TopN 的读者要的是"最大的前 N 个"，缺值不该占位
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            int cmp = a.compareTo(b);
            return ascending ? cmp : -cmp;
        });

        if (rows.size() <= n) {
            return;
        }
        if (mergeRestAs == null) {
            // 纯截断：不需要权重，也就不要求调用方带 __n 列
            List<JSONObject> kept = new java.util.ArrayList<>(rows.subList(0, n));
            rows.clear();
            rows.addAll(kept);
            return;
        }

        List<JSONObject> rest = new java.util.ArrayList<>(rows.subList(n, rows.size()));
        rows.subList(n, rows.size()).clear();
        rows.add(merge(rest, aliasAgg, dimAlias, mergeRestAs));
    }

    /** 把剩余分组合成一行 */
    private static JSONObject merge(List<JSONObject> rest, Map<String, String> aliasAgg, String dimAlias, String label) {
        JSONObject merged = new JSONObject();
        merged.put(dimAlias, label);
        BigDecimal weight = sum(rest, WEIGHT_ALIAS);
        for (Map.Entry<String, String> entry : aliasAgg.entrySet()) {
            String alias = entry.getKey();
            String agg = entry.getValue();
            switch (agg) {
                case "SUM", "COUNT" -> merged.put(alias, sum(rest, alias));
                case "MAX" -> merged.put(alias, extreme(rest, alias, true));
                case "MIN" -> merged.put(alias, extreme(rest, alias, false));
                case "AVG" -> merged.put(alias, weightedAverage(rest, alias, weight));
                default -> throw new ServiceException("聚合函数 " + agg + " 无法归并进「其他」行："
                        + "从分组结果推不出正确的值（去重计数需要原始行），请去掉「其他」归并或换聚合方式");
            }
        }
        return merged;
    }

    /**
     * AVG 必须按组内行数加权：直接对分组平均值再取平均，等于把一行数据的组与一万行的组看作等权。
     * 没有权重列时明确失败——一个看起来合理的错数字比报错难查得多。
     */
    private static BigDecimal weightedAverage(List<JSONObject> rest, String alias, BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("归并「其他」缺少分组行数权重，无法计算 AVG");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (JSONObject row : rest) {
            BigDecimal value = decimal(row.get(alias));
            BigDecimal count = decimal(row.get(WEIGHT_ALIAS));
            if (value == null || count == null) {
                continue;
            }
            total = total.add(value.multiply(count));
        }
        return total.divide(weight, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<JSONObject> rows, String alias) {
        BigDecimal total = null;
        for (JSONObject row : rows) {
            BigDecimal value = decimal(row.get(alias));
            if (value == null) {
                continue;
            }
            total = total == null ? value : total.add(value);
        }
        return total;
    }

    private static BigDecimal extreme(List<JSONObject> rows, String alias, boolean max) {
        BigDecimal picked = null;
        for (JSONObject row : rows) {
            BigDecimal value = decimal(row.get(alias));
            if (value == null) {
                continue;
            }
            if (picked == null || (max ? value.compareTo(picked) > 0 : value.compareTo(picked) < 0)) {
                picked = value;
            }
        }
        return picked;
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null) {
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
