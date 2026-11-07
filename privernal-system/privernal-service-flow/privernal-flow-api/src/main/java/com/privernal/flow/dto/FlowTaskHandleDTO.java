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
import java.util.Map;

/**
 * 流程任务操作参数（通过/驳回/委托/转办/加减签/跳转等）
 *
 * @author saperliu
 */
@Data
public class FlowTaskHandleDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务id */
    private Long taskId;

    /** 审批意见 */
    private String message;

    /** 流程变量（表单数据变更） */
    private Map<String, Object> variable;

    /** 驳回类型：LAST 上一节点 / START 发起节点 / TARGET 指定节点 */
    private String rejectType;

    /** 驳回/跳转目标节点编码 */
    private String targetNodeCode;

    /** 跳转类型（跳转接口使用）：PASS / REJECT */
    private String skipType;

    /** 目标办理人（委托/转办/加签为新增人员，减签为移除人员） */
    private List<String> userIds;

    /** 办理人用户id（内部服务调用无登录态时必传，不传则取当前登录用户） */
    private String handler;
}
