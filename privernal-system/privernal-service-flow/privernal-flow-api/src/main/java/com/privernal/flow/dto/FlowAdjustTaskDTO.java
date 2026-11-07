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
import java.util.List;

/**
 * 流程任务调整参数（管理员调整运行中流程的节点/审批人）
 *
 * @author saperliu
 */
@Data
public class FlowAdjustTaskDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务id */
    private Long taskId;

    /** 目标办理人（更换审批人时使用） */
    private List<String> userIds;

    /** 调整类型：CHANGE_ASSIGNEE 更换审批人 / CHANGE_NODE 调整节点 */
    private String adjustType;

    /** 目标节点编码（调整节点时使用，跳转到此节点） */
    private String targetNodeCode;

    /** 调整原因 */
    private String message;

    /** 操作人用户id（内部服务调用无登录态时必传，不传则取当前登录用户） */
    private String handler;
}
