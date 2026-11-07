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
 * 流程实例状态结果
 *
 * @author saperliu
 */
@Data
public class FlowInstanceStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程实例id */
    private Long instanceId;

    /** 流程定义编码 */
    private String flowCode;

    /** 业务数据id */
    private String businessId;

    /** 业务数据所在的动态表单编码（按流程编码解析，前端据此渲染申请表单） */
    private String tableCode;

    /** warm-flow 流程状态（0待提交 1审批中 2审批通过 4终止 5作废 6撤销 8已完成 9已退回 11拿回） */
    private String flowStatus;

    /** 业务数据状态（0草稿 1审批完成 2运行中 3取消，对应 WorkflowDataStatus） */
    private Integer dataStatus;

    /** 是否运行中 */
    private Boolean running;

    /** 当前节点编码 */
    private String nodeCode;

    /** 当前节点名称 */
    private String nodeName;
}
