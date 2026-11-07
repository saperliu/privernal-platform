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

package com.privernal.flow.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程表单内置按钮集（初始化草稿版本时按此生成按钮权限明细，对齐旧 wfs 的 workflow_instance_form_btn）
 *
 * @author saperliu
 */
@Getter
@AllArgsConstructor
public enum FlowFormBtnEnum {

    /** 保存（提交前） */
    SAVE("save", "保存"),

    /** 查看申请单（只读表单视图，不执行业务动作，显隐由使用方配置） */
    VIEW_FORM("viewForm", "申请单"),

    /** 审批通过 */
    APPROVE("approve", "通过"),

    /** 驳回 */
    REJECT("reject", "驳回"),

    /** 转办 */
    TRANSFER("transfer", "转办"),

    /** 委派 */
    DELEGATE("delegate", "委派"),

    /** 加签 */
    ADD_SIGN("addSign", "加签"),

    /** 减签 */
    REDUCE_SIGN("reduceSign", "减签"),

    /** 跳转 */
    JUMP("jump", "跳转"),

    /** 终止 */
    TERMINATE("terminate", "终止"),

    /** 撤销 */
    REVOKE("revoke", "撤销");

    /** 规范编码（前端 BUILTIN_BTNS、按钮权限明细的 btn_code 都用它） */
    private final String code;

    /** 默认中文名（别名 alias_name 为空时的展示文案） */
    private final String name;

    /**
     * 全量内置按钮（保持声明顺序）
     */
    public static Map<String, FlowFormBtnEnum> allButtons() {
        Map<String, FlowFormBtnEnum> buttons = new LinkedHashMap<>();
        for (FlowFormBtnEnum btn : values()) {
            buttons.put(btn.getCode(), btn);
        }
        return buttons;
    }
}
