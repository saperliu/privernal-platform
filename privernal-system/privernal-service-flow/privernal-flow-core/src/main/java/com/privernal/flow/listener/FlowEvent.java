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

package com.privernal.flow.listener;

import org.springframework.context.ApplicationEvent;

/**
 * 流程事件（用于 Spring 事件机制传播工作流事件）
 *
 * @author saperliu
 */
public class FlowEvent extends ApplicationEvent {

    private final String eventType;
    private final Long instanceId;
    private final String businessId;
    private final String flowCode;
    private final String flowName;
    private final String nodeCode;
    private final String handler;
    private final String message;

    public FlowEvent(Object source, String eventType, Long instanceId, String businessId,
                     String flowCode, String flowName, String nodeCode, String handler, String message) {
        super(source);
        this.eventType = eventType;
        this.instanceId = instanceId;
        this.businessId = businessId;
        this.flowCode = flowCode;
        this.flowName = flowName;
        this.nodeCode = nodeCode;
        this.handler = handler;
        this.message = message;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public String getBusinessId() {
        return businessId;
    }

    public String getFlowCode() {
        return flowCode;
    }

    public String getFlowName() {
        return flowName;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public String getHandler() {
        return handler;
    }

    public String getMessage() {
        return message;
    }

    /** 流程启动 */
    public static final String TYPE_START = "START";
    /** 流程审批通过 */
    public static final String TYPE_APPROVE = "APPROVE";
    /** 流程驳回 */
    public static final String TYPE_REJECT = "REJECT";
    /** 流程终止 */
    public static final String TYPE_TERMINATE = "TERMINATE";
    /** 流程撤销 */
    public static final String TYPE_REVOKE = "REVOKE";
    /** 流程完成 */
    public static final String TYPE_COMPLETE = "COMPLETE";
    /** 流程重启 */
    public static final String TYPE_RESTART = "RESTART";
    /** 任务签收 */
    public static final String TYPE_CLAIM = "CLAIM";
    /** 任务转办 */
    public static final String TYPE_TRANSFER = "TRANSFER";
    /** 超时自动处理 */
    public static final String TYPE_TIMEOUT = "TIMEOUT";
}
