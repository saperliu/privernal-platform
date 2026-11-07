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

package com.privernal.dynamic.advanced.service;

/**
 * 联动规则调度任务 {@code biz_key} 的<b>唯一</b>构造入口（task-upgrade P0-2）。
 * <p>
 * 为什么必须有这个类：到期提醒任务原先在四处各自手拼字符串，其中"保存钩子用逻辑
 * tableCode、每日重算用物理 tableName"两种写法并存过一次——同一条数据因此注册出两个
 * 不同 key 的任务，重算的"取消"永远打不中保存钩子注册的那条，表现为
 * <b>删了规则/改了日期但提醒照发、且任务只增不减吃满组织配额</b>。key 形状一旦分散就有
 * 再次漂移的风险，所以注册、改期、取消、级联取消全部经由此处取键。
 * </p>
 * <p>
 * <b>tableCode 口径</b>：一律传 {@code dynamic_form.table_code} 的值（该列存的是含组织前缀的
 * 物理表名，全链路同源）。禁止在某一条链路上改用"去前缀的逻辑名"——两边不一致就是 P0-2 的复发。
 * </p>
 *
 * @author saperliu
 */
public final class LinkageBizKeys {

    /** 任务来源：TIMER 型规则（周期调度） */
    public static final String SOURCE_TIMER = "DESIGNER";
    /** 任务来源：DATE_FIELD 到期字段（每行数据一个一次性任务） */
    public static final String SOURCE_DATE_FIELD = "DESIGNER_DATA";

    private static final String TIMER_PREFIX = "link:";
    private static final String DATE_PREFIX = "linkdate:";

    private LinkageBizKeys() {
    }

    /**
     * TIMER 规则的任务键：{@code link:{tableCode}:{ruleCode}}
     */
    public static String timer(String tableCode, String ruleCode) {
        return TIMER_PREFIX + require(tableCode, "tableCode") + ":" + require(ruleCode, "ruleCode");
    }

    /**
     * 某张表单下全部 TIMER 规则的前缀（表单删除级联取消用）
     */
    public static String timerByTable(String tableCode) {
        return TIMER_PREFIX + require(tableCode, "tableCode") + ":";
    }

    /**
     * DATE_FIELD 单行到期任务键：{@code linkdate:{tableCode}:{ruleCode}:{dataId}}
     */
    public static String dateExpiry(String tableCode, String ruleCode, String dataId) {
        return DATE_PREFIX + require(tableCode, "tableCode") + ":"
                + require(ruleCode, "ruleCode") + ":" + require(dataId, "dataId");
    }

    /**
     * 某条 DATE_FIELD 规则下全部 per-row 任务的前缀（规则删除/停用/参数变更时用）
     */
    public static String dateExpiryByRule(String tableCode, String ruleCode) {
        return DATE_PREFIX + require(tableCode, "tableCode") + ":" + require(ruleCode, "ruleCode") + ":";
    }

    /**
     * 某张表单下全部 DATE_FIELD 任务的前缀（表单删除级联取消用）
     */
    public static String dateExpiryByTable(String tableCode) {
        return DATE_PREFIX + require(tableCode, "tableCode") + ":";
    }

    /**
     * key 片段非空校验：空值会拼出 {@code linkdate::rule:dataId} 这类"看起来能匹配"的前缀，
     * 命中范围不可控，因此宁可直接抛出也不放行。
     */
    private static String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("调度 biz_key 的 " + name + " 不能为空");
        }
        return value.trim();
    }
}
