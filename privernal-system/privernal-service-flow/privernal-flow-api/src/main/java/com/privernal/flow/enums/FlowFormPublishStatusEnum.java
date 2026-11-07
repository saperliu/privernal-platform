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
 * 流程表单配置版本发布状态
 *
 * @author saperliu
 */
@Getter
@AllArgsConstructor
public enum FlowFormPublishStatusEnum {

    /** 草稿，可编辑 */
    DRAFT(0, "草稿"),

    /** 已发布，只读，运行时按 current_flag 取生效版本 */
    PUBLISHED(1, "已发布");

    private final int code;

    private final String desc;

    public static FlowFormPublishStatusEnum ofCode(Integer code) {
        if (code != null && code == PUBLISHED.code) {
            return PUBLISHED;
        }
        return DRAFT;
    }
}
