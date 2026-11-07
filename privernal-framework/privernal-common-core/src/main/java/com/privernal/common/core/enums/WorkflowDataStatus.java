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

package com.privernal.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作流数据的状态
 *
 * @author saperliu
 */
@Getter
@AllArgsConstructor
public enum WorkflowDataStatus {
    STAGING(0L, "草稿"), FINISH(1L, "已完成"), PROCESS(2L, "进行中"), CANCEL(3L, "已取消"), SUSPENDED(4L, "挂起");

    private final Long code;
    private final String info;

    public static String geDataInfoByCode(long code) {
        for (WorkflowDataStatus value : values()) {
            if (value.code.equals(code)) {
                if (value.code.equals(1L)) {
                    return "启用";
                }
                if (value.code.equals(3L)) {
                    return "禁用";
                }
                return value.info;
            }
        }
        return "";
    }

    public static String geWorkflowInfoByCode(long code) {
        for (WorkflowDataStatus value : values()) {
            if (value.code.equals(code)) {
                return value.info;
            }
        }
        return "";
    }
}
