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

package com.privernal.flow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.warm.flow.orm.entity.FlowInstance;

/**
 * 流程实例列表行：实例字段 + 业务表单基础数据（实例 variable 中的业务标题）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowInstanceListDTO extends FlowInstance {

    /** 流程编码（按钮权限配置按此加载） */
    private String flowCode;

    /** 业务表单标题（动态表单数据的 title 字段） */
    private String businessTitle;
}
