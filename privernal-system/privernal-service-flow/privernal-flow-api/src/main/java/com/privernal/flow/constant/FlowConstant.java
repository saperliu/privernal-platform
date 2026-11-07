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

package com.privernal.flow.constant;

/**
 * 工作流模块常量
 *
 * @author saperliu
 */
public class FlowConstant {

    /** 权限标识分隔符（warm-flow 约定，多个办理人权限标识用 @@ 分隔） */
    public static final String PERMISSION_FLAG_SEPARATOR = "@@";

    /** 权限标识中类型与值之间的分隔符，如 user:123、role:456 */
    public static final String PERMISSION_TYPE_SEPARATOR = ":";

    /** 动态表单绑定的流程定义 JSON 扩展键 */
    public static final String EXT_TABLE_CODE = "tableCode";

    private FlowConstant() {
    }
}
