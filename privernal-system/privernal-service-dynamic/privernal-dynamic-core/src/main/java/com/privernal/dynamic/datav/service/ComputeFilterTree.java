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

import java.util.function.Function;

/**
 * 过滤条件树（M4.3）：{@code {op: and|or, when:[ 叶子 | 子树 ]}}，叶子是
 * {@code {field, op, value}}，op 取 {@link ComputeSpecs#FILTER_OPS}。
 * <p>
 * 结构遍历与校验只在这里写一遍，两条计算路径各自提供叶子实现：
 * SQL 下推路径把叶子翻译成带参数绑定的条件串，内存聚合路径直接对行求值。
 * 分成两份遍历迟早会出现「同一棵树在一边成立、另一边不成立」。
 * <p>
 * 旧的平铺 {@code filters:[...]} 数组按 and 树解释（{@link #normalize}），所以历史配置行为不变。
 *
 * @author saperliu
 */
public final class ComputeFilterTree {

    /** 嵌套深度上限：树是用户可控输入，不设上限等于让一次请求把栈打满 */
    public static final int MAX_DEPTH = 8;

    private ComputeFilterTree() {
    }

    /**
     * 把「树 + 旧的平铺数组」归一成一颗树。两者都没配时返回 null。
     * <p>
     * 同时给了树又给了数组时按 and 合并——不是「树优先、数组作废」：
     * 那会让界面上已经填过的条件静默失效，用户看到的结果比预期宽。
     */
    public static JSONObject normalize(JSONObject tree, JSONArray flat) {
        JSONObject flatAsTree = null;
        if (flat != null && !flat.isEmpty()) {
            JSONObject built = new JSONObject();
            built.put("op", "and");
            built.put("when", flat);
            flatAsTree = built;
        }
        JSONObject normalized = normalizeNode(tree, 1);
        if (normalized == null) {
            return flatAsTree;
        }
        if (flatAsTree == null) {
            return normalized;
        }
        JSONObject merged = new JSONObject();
        merged.put("op", "and");
        JSONArray when = new JSONArray();
        when.add(normalized);
        when.add(flatAsTree);
        merged.put("when", when);
        return merged;
    }

    /**
     * 递归遍历并校验结构，叶子交给 {@code leaf} 判定。
     *
     * @param leaf 叶子求值；返回 false 表示这一支不成立
     */
    public static boolean evaluate(JSONObject node, Function<JSONObject, Boolean> leaf) {
        return evaluate(node, leaf, 1);
    }

    private static boolean evaluate(JSONObject node, Function<JSONObject, Boolean> leaf, int depth) {
        if (depth > MAX_DEPTH) {
            throw new ServiceException("过滤条件嵌套超过 " + MAX_DEPTH + " 层上限");
        }
        String op = StringUtils.trimToEmpty(node.getString("op")).toLowerCase();
        if (!"and".equals(op) && !"or".equals(op)) {
            return leaf.apply(node);
        }
        JSONArray when = node.getJSONArray("when");
        if (when == null || when.isEmpty()) {
            // 空的条件组不能当成「全部成立」：and 的空集恒真是数学惯例，但在这里它只会
            // 掩盖一个配坏了的条件组，让用户拿到比预期宽得多的数据
            throw new ServiceException("过滤条件组 " + op + " 里没有任何条件");
        }
        for (Object item : when) {
            JSONObject child = childOf(item, depth);
            boolean pass = evaluate(child, leaf, depth + 1);
            if ("and".equals(op) && !pass) {
                return false;
            }
            if ("or".equals(op) && pass) {
                return true;
            }
        }
        return "and".equals(op);
    }

    /** 数组元素可能是对象，也可能是 Jackson 绑出来的 LinkedHashMap */
    private static JSONObject childOf(Object item, int depth) {
        if (depth > MAX_DEPTH) {
            throw new ServiceException("过滤条件嵌套超过 " + MAX_DEPTH + " 层上限");
        }
        if (item instanceof JSONObject object) {
            return object;
        }
        if (item instanceof java.util.Map<?, ?> map) {
            return JSONObject.from(map);
        }
        throw new ServiceException("过滤条件项必须是对象");
    }

    /** 判断一个节点是条件组还是叶子 */
    public static boolean isGroup(JSONObject node) {
        String op = StringUtils.trimToEmpty(node.getString("op")).toLowerCase();
        return "and".equals(op) || "or".equals(op);
    }

    /**
     * 校验并返回可用的节点；null / 空对象一律归一成 null（表示「没有这个条件」）。
     * <p>
     * 空对象必须当成没配而不是空条件组：界面上「高级过滤」区域留着没填时就是 {@code {}}，
     * 判成空组会让整块屏突然报错。
     */
    private static JSONObject normalizeNode(JSONObject node) {
        return normalizeNode(node, 1);
    }

    /**
     * 深度上限必须卡在这里，而不是只卡在 {@link #evaluate}：归一化本身是递归的，
     * 一个足够深的树在走到求值之前就已经把栈打满了——抛 StackOverflowError 既不可读，
     * 也会绕过所有 ServiceException 的错误分类。
     */
    private static JSONObject normalizeNode(JSONObject node, int depth) {
        if (depth > MAX_DEPTH) {
            throw new ServiceException("过滤条件嵌套超过 " + MAX_DEPTH + " 层上限");
        }
        if (node == null || node.isEmpty()) {
            return null;
        }
        if (isGroup(node)) {
            JSONArray raw = node.getJSONArray("when");
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            JSONArray kept = new JSONArray();
            for (Object item : raw) {
                JSONObject child = childOf(item, depth);
                JSONObject normalized = normalizeNode(child, depth + 1);
                if (normalized != null) {
                    kept.add(normalized);
                } else if (isGroup(child)) {
                    // 组里所有子条件都没填 → 这个组本身也不成立，丢掉而不是留一个空组
                    continue;
                } else {
                    kept.add(child);
                }
            }
            if (kept.isEmpty()) {
                return null;
            }
            if (kept.size() == 1 && !isGroup(kept.getJSONObject(0))) {
                return kept.getJSONObject(0);
            }
            JSONObject rebuilt = new JSONObject();
            rebuilt.put("op", node.getString("op"));
            rebuilt.put("when", kept);
            return rebuilt;
        }
        return isBlankLeaf(node) ? null : node;
    }

    /** 字段或值没填的叶子：视为未配置，不参与过滤 */
    private static boolean isBlankLeaf(JSONObject node) {
        return StringUtils.isBlank(node.getString("field")) || node.get("value") == null
                || "".equals(node.get("value"));
    }

    /** 供两侧复用：取出叶子的 op（默认 eq） */
    public static String leafOp(JSONObject leaf) {
        return StringUtils.defaultIfBlank(leaf.getString("op"), "eq").toLowerCase();
    }
}
