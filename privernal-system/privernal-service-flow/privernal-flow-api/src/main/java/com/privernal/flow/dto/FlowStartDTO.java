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
import java.util.Map;

/**
 * 流程启动参数
 * 动态表单数据提交后由 dynamic 模块自动调用，业务模块也可显式调用
 *
 * @author saperliu
 */
@Data
public class FlowStartDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程定义编码（与 dynamic_form.workflow_code 对应，二选一：flowCode / tableCode） */
    private String flowCode;

    /** 动态表单编码（按绑定关系解析流程定义，二选一：flowCode / tableCode） */
    private String tableCode;

    /** 业务数据id（动态表单数据主键），必传 */
    private String businessId;

    /** 流程变量（表单字段值，供互斥网关条件与脚本审批人解析使用） */
    private Map<String, Object> variables;

    /** 发起人用户id（不传则取当前登录用户） */
    private String handler;
}
