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

import java.io.Serial;
import java.io.Serializable;

/**
 * 组织同步初始化流程配置参数
 * 将平台模板的表单绑定与节点权限配置复制到目标组织
 *
 * @author saperliu
 */
@Data
public class FlowDefinitionSynDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标组织id，必传 */
    private String organizationId;

    /** 目标部门id（为空时取组织id） */
    private String departmentId;
}
