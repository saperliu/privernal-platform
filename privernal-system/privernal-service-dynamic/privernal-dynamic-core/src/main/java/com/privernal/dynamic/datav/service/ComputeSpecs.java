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

import java.util.Set;

/**
 * 报表计算层的共享语义白名单（M4）。
 * <p>
 * 同一套聚合/粒度/操作符语义有**两条实现路径**：动态表走 SQL 下推
 * （{@code com.privernal.dynamic.report.service.DynamicReportQueryService}），外部源走内存聚合
 * （{@link DatavRowAggregator}）。M4 的验收是「同一数据集在报表、大屏、导出三处数值完全一致」，
 * 而这两条路径此前各自持有一份白名单常量——2026-10-18 加 HOUR/MINUTE 粒度时，
 * 只改内存侧那一处就漏了 SQL 侧，同一个粒度在两种源上一个能出一个报「不支持」。
 * <p>
 * 因此白名单只在这里定义一次，两条路径都引用它。新增语义时的正确顺序是：
 * 先加进本类，再让两条路径分别实现，最后写两侧对照用例——只实现一边就是制造不一致。
 *
 * @author saperliu
 */
public final class ComputeSpecs {

    /**
     * 聚合函数白名单。
     * <p>
     * COUNTD（去重计数）能进来是因为两条路径都能忠实实现：SQL 侧有 `COUNT(DISTINCT x)`，
     * 内存侧靠 HashSet。MEDIAN/PERCENTILE 这类 MySQL 没有原生函数、只能用窗口函数凑的聚合
     * **不在名单里**——在一条路径上给近似值、另一条给精确值，比两边都直接报「不支持」危险得多。
     */
    public static final Set<String> AGG_FUNCS = Set.of("SUM", "COUNT", "COUNTD", "AVG", "MAX", "MIN");

    /**
     * 时间分组粒度。HOUR/MINUTE 的输出必须带日期部分，否则跨天的同一小时会被合并成一组，
     * 「小时趋势」图画出来的其实是跨天累计——两侧实现都要守这条。
     */
    public static final Set<String> GRANULARITIES = Set.of("MINUTE", "HOUR", "DAY", "WEEK", "MONTH", "YEAR");

    /**
     * 过滤操作符。运行态参数绑定（{@link DynamicDatavDataService}）与内存聚合的运行时过滤
     * 共用这一份，前端绑定面板的下拉选项也必须与它一致，否则用户能选出一个后端拒绝的操作符。
     */
    public static final Set<String> FILTER_OPS = Set.of("eq", "ne", "like", "gt", "ge", "lt", "le");

    /** 单图表度量数上限：与前端绑定面板的 MAX_MEASURES 同值 */
    public static final int MAX_MEASURES = 5;

    private ComputeSpecs() {
    }

    /** 给报错文案用：白名单顺序固定，便于用户按提示逐项核对 */
    public static String aggHint() {
        return "可用 SUM/COUNT/COUNTD/AVG/MAX/MIN";
    }

    public static String granularityHint() {
        return "可用 MINUTE/HOUR/DAY/WEEK/MONTH/YEAR";
    }
}
