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

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.feign.RemoteDynamicService;
import com.privernal.flow.context.FlowContextHolder;
import com.privernal.system.api.WebhookPublisher;
import com.privernal.system.constant.WebhookEventTypes;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 流程事件发布器（在流程操作完成后发布事件，供监听器处理）
 *
 * @author saperliu
 */
@Slf4j
@Component
public class FlowEventPublisher {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private WebhookPublisher webhookPublisher;

    @Autowired
    private RemoteDynamicService remoteDynamicService;

    public void publishStart(Instance instance) {
        publish(FlowEvent.TYPE_START, instance, null, null);
    }

    public void publishApprove(Instance instance, String nodeCode) {
        publish(FlowEvent.TYPE_APPROVE, instance, nodeCode, null);
    }

    public void publishReject(Instance instance, String nodeCode) {
        publish(FlowEvent.TYPE_REJECT, instance, nodeCode, null);
    }

    public void publishTerminate(Instance instance, String message) {
        publish(FlowEvent.TYPE_TERMINATE, instance, null, message);
    }

    public void publishRevoke(Instance instance, String message) {
        publish(FlowEvent.TYPE_REVOKE, instance, null, message);
    }

    public void publishComplete(Instance instance) {
        publish(FlowEvent.TYPE_COMPLETE, instance, null, null);
    }

    public void publishRestart(Instance instance) {
        publish(FlowEvent.TYPE_RESTART, instance, null, null);
    }

    public void publishTimeout(Long instanceId, String flowCode, String businessId, String message) {
        FlowEvent event = new FlowEvent(this, FlowEvent.TYPE_TIMEOUT, instanceId, businessId,
                flowCode, null, null, SecurityUtils.getUserId(), message);
        eventPublisher.publishEvent(event);
    }

    private void publish(String type, Instance instance, String nodeCode, String message) {
        if (instance == null) {
            return;
        }
        String flowCode = null;
        String flowName = null;
        if (instance.getDefinitionId() != null) {
            Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
            if (definition != null) {
                flowCode = definition.getFlowCode();
                flowName = definition.getFlowName();
            }
        }
        FlowEvent event = new FlowEvent(this, type, instance.getId(), instance.getBusinessId(),
                flowCode, flowName, nodeCode, SecurityUtils.getUserId(), message);
        eventPublisher.publishEvent(event);
        publishWebhook(type, instance, flowCode, flowName, nodeCode, message);
    }

    /**
     * 流程状态变更对外通知：只把「发起/完成/驳回」映射成 Webhook 事件，
     * 签收、转办这类过程动作对集成方没有业务含义，不做事件类型膨胀。
     */
    private void publishWebhook(String type, Instance instance, String flowCode, String flowName,
                                String nodeCode, String message) {
        String eventType = switch (type) {
            case FlowEvent.TYPE_START -> WebhookEventTypes.FLOW_STARTED;
            case FlowEvent.TYPE_COMPLETE -> WebhookEventTypes.FLOW_FINISHED;
            case FlowEvent.TYPE_REJECT -> WebhookEventTypes.FLOW_REJECTED;
            default -> null;
        };
        if (eventType == null) {
            return;
        }
        String organizationId = currentOrganizationId();
        if (StringUtils.isEmpty(organizationId)) {
            log.warn("流程事件缺少组织归属，跳过 Webhook 通知 type={} instanceId={}", type, instance.getId());
            return;
        }
        JSONObject data = new JSONObject();
        data.put("instanceId", instance.getId());
        data.put("businessId", instance.getBusinessId());
        data.put("flowCode", flowCode);
        data.put("flowName", flowName);
        data.put("flowStatus", instance.getFlowStatus());
        data.put("nodeCode", nodeCode);
        data.put("nodeName", instance.getNodeName());
        data.put("handler", SecurityUtils.getUserId());
        data.put("message", message);
        webhookPublisher.publish(organizationId, eventType, resolveTableCode(flowCode),
                instance.getBusinessId(), data);
    }

    /**
     * 流程发起时上下文里已有组织，其余动作（审批、驳回）走登录态，两处都取不到就不通知
     */
    private String currentOrganizationId() {
        String organizationId = OrganizationContextHolder.getOrganizationId();
        if (StringUtils.isNotEmpty(organizationId)) {
            return organizationId;
        }
        FlowContextHolder.FlowContext context = FlowContextHolder.get();
        return context == null ? null : context.getOrganizationId();
    }

    /**
     * 流程定义可能没绑定动态表（纯流程），反查失败只让订阅的表过滤失效，不影响流程本身
     */
    private String resolveTableCode(String flowCode) {
        if (StringUtils.isEmpty(flowCode)) {
            return null;
        }
        try {
            return remoteDynamicService.findTableCodeByFlowCode(flowCode);
        } catch (Exception e) {
            log.warn("按流程编码反查 tableCode 失败 flowCode={}", flowCode, e);
            return null;
        }
    }
}
