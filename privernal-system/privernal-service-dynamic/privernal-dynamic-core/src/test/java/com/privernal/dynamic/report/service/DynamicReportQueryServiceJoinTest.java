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

package com.privernal.dynamic.report.service;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 白名单关联（M3-2）FROM 子句拼装测试：ON 形态是行级权限与软删语义的载体，
 * 拼错一个别名，行级范围条件（t0. 前缀）就会落在错误的表上或直接 SQL 报错。
 *
 * @author saperliu
 */
@DisplayName("白名单关联 FROM 子句拼装测试")
class DynamicReportQueryServiceJoinTest {

    @Test
    @DisplayName("无关联：主表恒别名 t0（数据范围切面 mainAlias=t0 按别名限定行级条件）")
    void noJoinStillAliased() {
        assertThat(DynamicReportQueryService.buildFromClause("f_main", List.of())).isEqualTo("f_main t0");
        assertThat(DynamicReportQueryService.buildFromClause("f_main", null)).isEqualTo("f_main t0");
    }

    @Test
    @DisplayName("子表关联：ON parent_id + 关联表软删条件进 ON")
    void subTableJoin() {
        JSONObject join = new JSONObject();
        join.put("type", "subTable");
        join.put("field", "items");
        join.put("physicalTable", "f_main_items");
        String sql = DynamicReportQueryService.buildFromClause("f_main", List.of(join));
        assertThat(sql).isEqualTo("f_main t0 INNER JOIN f_main_items t1"
                + " ON t1.parent_id = t0.id AND t1.deleted_flag = 'N'");
    }

    @Test
    @DisplayName("引用关联：ON 目标表 id = 主表.关联字段")
    void relationJoin() {
        JSONObject join = new JSONObject();
        join.put("type", "relation");
        join.put("field", "customer_id");
        join.put("physicalTable", "f_customer");
        String sql = DynamicReportQueryService.buildFromClause("f_order", List.of(join));
        assertThat(sql).isEqualTo("f_order t0 INNER JOIN f_customer t1"
                + " ON t1.id = t0.customer_id AND t1.deleted_flag = 'N'");
    }

    @Test
    @DisplayName("多个关联按序编号 t1/t2，不重排既有组件的语义")
    void multipleJoins() {
        JSONObject sub = new JSONObject();
        sub.put("type", "subTable");
        sub.put("field", "items");
        sub.put("physicalTable", "f_main_items");
        JSONObject rel = new JSONObject();
        rel.put("type", "relation");
        rel.put("field", "customer_id");
        rel.put("physicalTable", "f_customer");
        String sql = DynamicReportQueryService.buildFromClause("f_main", List.of(sub, rel));
        assertThat(sql).contains("INNER JOIN f_main_items t1 ON t1.parent_id = t0.id");
        assertThat(sql).contains("INNER JOIN f_customer t2 ON t2.id = t0.customer_id");
        assertThat(sql.indexOf("t1")).isLessThan(sql.indexOf("t2"));
    }
}
