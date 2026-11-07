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

package com.privernal.dynamic.advanced.engine;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联动动作配置（rules.actions 数组的元素，§7.4）：{type, config, stopOnFail}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionConfig {

    /** 动作类型：DATA_UPDATE/DATA_CREATE/DATA_DELETE/MSG_INNER/... */
    private String type;

    /** 动作参数（各 handler 自解释） */
    private JSONObject config;

    /** 该动作失败时是否中止后续动作（规则级 failPolicy 之外的单动作开关） */
    private Boolean stopOnFail;

    public boolean isStopOnFail() {
        return Boolean.TRUE.equals(stopOnFail);
    }
}
