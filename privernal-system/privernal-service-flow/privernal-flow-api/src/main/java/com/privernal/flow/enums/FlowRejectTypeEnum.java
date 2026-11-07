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

/**
 * 驳回（退回）目标类型
 *
 * @author saperliu
 */
@Getter
@AllArgsConstructor
public enum FlowRejectTypeEnum {

    /** 退回上一节点 */
    LAST("LAST", "上一节点"),

    /** 退回发起（草稿）节点 */
    START("START", "发起节点"),

    /** 退回指定节点 */
    TARGET("TARGET", "指定节点");

    private final String code;

    private final String desc;

    public static FlowRejectTypeEnum ofCode(String code) {
        for (FlowRejectTypeEnum type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return LAST;
    }
}
