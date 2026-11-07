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

package com.privernal.flow;

import com.privernal.flow.handler.condition.FlowExprConditionStrategy;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关条件表达式的引擎级回归：把 FlowExprConditionStrategy 注册进 warm-flow 策略链后，
 * 走网关路由实际使用的入口 ExpressionUtil.evalCondition 验证求值结果。
 *
 * <p>裸测没有 Spring 容器，策略注册需手动调 {@code register()}。</p>
 *
 * @author saperliu
 */
public class ExprTest {

    @Test
    @DisplayName("wfexpr 条件经网关路由入口求值")
    void gatewayConditionExpressions() {
        FlowExprConditionStrategy strategy = new FlowExprConditionStrategy();
        // 裸测没有容器，@PostConstruct 需手动触发（Spring 环境由容器自动调用）
        strategy.register();

        Map<String, Object> vars = new HashMap<>();
        vars.put("amount", 15000);
        vars.put("device", "设备A");
        vars.put("title", "十月保养单");
        vars.put("__initiator", "u001");
        vars.put("__initiator_dept", "d001");
        vars.put("__initiator_org", "o001");
        vars.put("__initiator_roles", List.of("质检员", "组长"));

        String json = "{\"expr\":\"%s\",\"labelMap\":{%s}}";
        String amountMap = "\"报销金额\":\"amount\"";
        String deviceMap = "\"设备\":\"device\"";
        String ctxMap = "\"发起人\":\"__initiator\",\"发起人部门\":\"__initiator_dept\"";

        // 引擎同一入口（getValue 按 wfexpr 前缀分发并剥离前缀后调 eval）
        check("标签翻译+数值比较-满足", ExpressionUtil.evalCondition(
                String.format("wfexpr@@" + json, "报销金额>10000", amountMap), vars), true);
        check("标签翻译+数值比较-不满足", ExpressionUtil.evalCondition(
                String.format("wfexpr@@" + json, "报销金额>10000", amountMap), Map.of("amount", 500)), false);
        check("字符串单引号比较", ExpressionUtil.evalCondition(
                String.format("wfexpr@@" + json, "设备=='设备A'", deviceMap), vars), true);
        check("多条件AND", ExpressionUtil.evalCondition(
                String.format("wfexpr@@" + json, "报销金额>10000 && 设备=='设备A'", amountMap + "," + deviceMap), vars), true);
        check("函数SUM", ExpressionUtil.evalCondition("wfexpr@@SUM(1,2,3)>5", vars), true);
        check("HAS_ROLE-命中", ExpressionUtil.evalCondition("wfexpr@@HAS_ROLE('质检员')", vars), true);
        check("HAS_ROLE-未命中", ExpressionUtil.evalCondition("wfexpr@@HAS_ROLE('厂长')", vars), false);
        check("HAS_ROLE-无角色变量(老实例容错)", ExpressionUtil.evalCondition(
                "wfexpr@@HAS_ROLE('厂长')", Map.of("amount", 1)), false);
        check("LEN/CONCAT", ExpressionUtil.evalCondition("wfexpr@@LEN(CONCAT(title,'x'))==6", vars), true);
        check("组织机构保留变量", ExpressionUtil.evalCondition(
                String.format("wfexpr@@" + json, "发起人=='u001'&&发起人部门=='d001'", ctxMap), vars), true);
        check("DAYS日期差", ExpressionUtil.evalCondition("wfexpr@@DAYS('2026-10-10','2026-10-01')==9", vars), true);
        check("IF/NUMBER", ExpressionUtil.evalCondition("wfexpr@@IF(NUMBER('8')>5,1,0)==1", vars), true);
        check("旧运算符条件不受影响", ExpressionUtil.evalCondition("eq@@amount|15000", vars), true);
    }

    private static void check(String name, boolean actual, boolean expect) {
        assertEquals(expect, actual, () -> name + " 期望 " + expect + "，实际 " + actual);
    }
}
