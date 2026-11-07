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
 * 流程节点信息（用于流程图高亮/回显）
 *
 * @author saperliu
 */
@Data
public class FlowNodeInfoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 节点编码 */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 节点类型（0开始 1审批 2抄送 3互斥网关 4并行网关 5结束） */
    private Integer nodeType;

    /** 节点状态：PENDING 未到达 / ACTIVE 正在办理 / COMPLETED 已完成 / REJECTED 已驳回 */
    private String nodeStatus;

    /** 办理人名称（当前节点正在办理时） */
    private String assigneeName;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;
}
