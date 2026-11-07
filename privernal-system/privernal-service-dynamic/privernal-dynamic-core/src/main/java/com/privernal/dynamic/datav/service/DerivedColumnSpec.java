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
import com.privernal.dynamic.report.domain.DynamicDataset;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 派生列（计算字段）的落地入口（M4.2）。
 * <p>
 * 定义放在数据集的列快照里（带 {@code expression} 的那些），所以计算字段是数据集的一等公民：
 * 报表、大屏、导出引用同一个 {@code [利润]} 得到同一个数，而不是每个图表各写一遍表达式。
 * <p>
 * 两条路径的接法不同，但都基于同一棵 AST（{@link ComputeExpression}）：
 * <ul>
 *   <li>内存路径在聚合<b>之前</b>把派生列补成普通列，于是聚合器完全不用改——它看到的
 *       就是多了一个字段的行，过滤/分组/聚合都能正常引用</li>
 *   <li>SQL 路径把表达式翻译成列表达式片段下推到库里，动态表不必把整表拉进内存</li>
 * </ul>
 *
 * @author saperliu
 */
public final class DerivedColumnSpec {

    private DerivedColumnSpec() {
    }

    /** fieldCode → 列定义（只含带 expression 的项）；解析失败即抛，所以返回值一定可用 */
    public static Map<String, JSONObject> of(DynamicDataset dataset) {
        Map<String, JSONObject> derived = new LinkedHashMap<>();
        JSONObject snapshot = dataset == null ? null : dataset.getColumnSnapshot();
        JSONArray columns = snapshot == null ? null : snapshot.getJSONArray("columns");
        if (columns == null) {
            return derived;
        }
        for (int i = 0; i < columns.size(); i++) {
            JSONObject column = columns.getJSONObject(i);
            if (column == null || StringUtils.isBlank(column.getString("expression"))) {
                continue;
            }
            String code = column.getString("fieldCode");
            if (StringUtils.isBlank(code)) {
                throw new ServiceException("计算字段缺少 fieldCode");
            }
            derived.put(code, column);
        }
        return derived;
    }

    /** 除派生列之外的普通列编码：表达式里的字段引用只能落在这些上面 */
    public static Map<String, String> baseTypes(DynamicDataset dataset, Map<String, JSONObject> derived) {
        Map<String, String> types = new LinkedHashMap<>();
        JSONObject snapshot = dataset == null ? null : dataset.getColumnSnapshot();
        JSONArray columns = snapshot == null ? null : snapshot.getJSONArray("columns");
        if (columns == null) {
            return types;
        }
        for (int i = 0; i < columns.size(); i++) {
            JSONObject column = columns.getJSONObject(i);
            String code = column == null ? null : column.getString("fieldCode");
            if (StringUtils.isBlank(code) || derived.containsKey(code)) {
                continue;
            }
            types.put(code, StringUtils.defaultIfBlank(column.getString("dataType"), "text"));
        }
        return types;
    }

    /**
     * 校验全部派生列并返回解析好的 AST。
     * <p>
     * 保存数据集时就要调用：表达式写错如果在取数时才暴露，用户看到的是一句
     * 「数据源查询失败」，而要定位到某个计算字段少写了一个括号得逐图去翻。
     * 派生列可以引用先前的派生列（按快照顺序），所以这里按顺序累积可引用集合。
     */
    public static Map<String, ComputeExpression.Node> validate(Map<String, JSONObject> derived,
                                                               Map<String, String> baseTypes) {
        Map<String, ComputeExpression.Node> parsed = new LinkedHashMap<>();
        Map<String, String> visible = new LinkedHashMap<>(baseTypes);
        for (Map.Entry<String, JSONObject> entry : derived.entrySet()) {
            String code = entry.getKey();
            if (visible.containsKey(code)) {
                throw new ServiceException("计算字段与已有列重名：" + code);
            }
            String expression = entry.getValue().getString("expression");
            ComputeExpression.Node node;
            try {
                node = ComputeExpression.parse(expression, visible);
            } catch (ServiceException e) {
                throw new ServiceException("计算字段「" + labelOf(entry.getValue(), code) + "」有问题：" + e.getMessage());
            }
            parsed.put(code, node);
            visible.put(code, StringUtils.defaultIfBlank(entry.getValue().getString("dataType"), "number"));
        }
        return parsed;
    }

    /**
     * 内存路径：把派生列补进行集合。
     * <p>
     * 必须在过滤与聚合之前调用——派生列常常正是被过滤/分组的对象（按「利润档」分组），
     * 放到聚合后就找不到它了。
     */
    public static void applyToRows(List<Map<String, Object>> rows, Map<String, ComputeExpression.Node> parsed,
                                   Map<String, String> baseTypes) {
        if (rows == null || rows.isEmpty() || parsed.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            // 行是可变 Map，派生列直接补进去；后面的聚合器把它当普通列看待
            Map<String, Object> scratch = new LinkedHashMap<>(row);
            for (Map.Entry<String, ComputeExpression.Node> entry : parsed.entrySet()) {
                Object value = entry.getValue().evaluate(scratch);
                row.put(entry.getKey(), value);
                scratch.put(entry.getKey(), value);
            }
        }
    }

    /** 派生列的类型并进列快照口径，供「度量必须数值列」这类校验使用 */
    public static void mergeTypes(Map<String, String> columnTypes, Map<String, JSONObject> derived) {
        derived.forEach((code, column) -> columnTypes.put(code,
                StringUtils.defaultIfBlank(column.getString("dataType"), "number")));
    }

    public static void mergeNames(Map<String, String> columnNames, Map<String, JSONObject> derived) {
        derived.forEach((code, column) -> columnNames.put(code, labelOf(column, code)));
    }

    /**
     * SQL 路径：派生列翻译成表达式，普通列原样返回。
     *
     * @param columnOf 普通列名 → 可进 SQL 的表达式（调用方负责标识符校验）
     */
    public static String sqlExpr(String field, Map<String, ComputeExpression.Node> parsed,
                                 java.util.function.Function<String, String> columnOf) {
        ComputeExpression.Node node = parsed.get(field);
        if (node == null) {
            return columnOf.apply(field);
        }
        // 递归而不是单层：计算字段可以引用先前的计算字段，内层那个同样是派生列。
        // 当成普通列名交给 columnOf，SQL 里就会出现 Unknown column —— 内存路径因为
        // 是按顺序把结果补进行集合，天然能读到，两条路径会在这一点上悄悄分叉。
        // 终止性由 validate() 保证：引用只能指向快照里排在它前面的列，环状定义进不了库。
        return node.toSql(ref -> sqlExpr(ref, parsed, columnOf));
    }

    /**
     * 把用户定义的派生列并回一份新产出的列快照。
     * <p>
     * 保存数据集时列快照会按数据源重新抽样（外部源）或按表单重新解析（动态表），
     * 那两个过程只认识物理列。不合并的话，用户加的计算字段会在下一次保存时被静默抹掉——
     * 而引用它的图表只会报「列不存在」，看不出是保存动作造成的。
     */
    public static void mergeInto(JSONArray physicalColumns, JSONArray submittedColumns) {
        if (physicalColumns == null || submittedColumns == null) {
            return;
        }
        java.util.Set<String> physical = new java.util.HashSet<>();
        for (int i = 0; i < physicalColumns.size(); i++) {
            JSONObject column = physicalColumns.getJSONObject(i);
            if (column != null && StringUtils.isNotBlank(column.getString("fieldCode"))) {
                physical.add(column.getString("fieldCode"));
            }
        }
        for (int i = 0; i < submittedColumns.size(); i++) {
            JSONObject column = submittedColumns.getJSONObject(i);
            String code = column == null ? null : column.getString("fieldCode");
            if (column == null || StringUtils.isBlank(code) || StringUtils.isBlank(column.getString("expression"))) {
                continue;
            }
            // 让物理列「优先」等于静默丢掉用户刚保存的计算字段：图表只会报列不存在，看不出是保存造成的
            if (physical.contains(code)) {
                throw new ServiceException("计算字段「" + labelOf(column, code) + "」与已有列重名：" + code);
            }
            physicalColumns.add(column);
            physical.add(code);
        }
    }

    /** 从一份列快照里挑出派生列（用于保存前取回用户已定义的项） */
    public static JSONArray submittedDerived(JSONObject snapshot) {
        JSONArray derived = new JSONArray();
        JSONArray columns = snapshot == null ? null : snapshot.getJSONArray("columns");
        if (columns == null) {
            return derived;
        }
        for (int i = 0; i < columns.size(); i++) {
            // 提交体是外部输入：元素不是列对象时说明请求结构写错了，要说清楚是哪一项，
            // 不能让 fastjson2 的类型转换异常变成一句没人能读懂的 500
            if (!(columns.get(i) instanceof Map)) {
                throw new ServiceException("列快照第 " + (i + 1) + " 项不是列定义对象");
            }
            JSONObject column = columns.getJSONObject(i);
            if (column != null && StringUtils.isNotBlank(column.getString("expression"))) {
                derived.add(column);
            }
        }
        return derived;
    }

    private static String labelOf(JSONObject column, String fallback) {
        return StringUtils.defaultIfBlank(column.getString("fieldName"), fallback);
    }
}
