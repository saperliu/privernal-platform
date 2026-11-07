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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

/**
 * 流程事件监听器（处理 warm-flow 触发的业务事件，如通知、MQ 推送等）
 *
 * @author saperliu
 */
@Component
@EnableAsync
public class FlowEventListener {

    private static final Logger log = LoggerFactory.getLogger(FlowEventListener.class);

    @Async
    @EventListener
    public void onFlowEvent(FlowEvent event) {
        log.info("流程事件: type={}, instanceId={}, businessId={}, flowCode={}, nodeCode={}, handler={}",
                event.getEventType(), event.getInstanceId(), event.getBusinessId(),
                event.getFlowCode(), event.getNodeCode(), event.getHandler());
        switch (event.getEventType()) {
            case FlowEvent.TYPE_START:
                log.info("流程启动通知：businessId={}, flowCode={}", event.getBusinessId(), event.getFlowCode());
                break;
            case FlowEvent.TYPE_APPROVE:
                log.info("流程审批通过：instanceId={}, nodeCode={}, handler={}", event.getInstanceId(), event.getNodeCode(), event.getHandler());
                break;
            case FlowEvent.TYPE_REJECT:
                log.info("流程驳回：instanceId={}, nodeCode={}, handler={}", event.getInstanceId(), event.getNodeCode(), event.getHandler());
                break;
            case FlowEvent.TYPE_TERMINATE:
                log.info("流程终止：instanceId={}, message={}", event.getInstanceId(), event.getMessage());
                break;
            case FlowEvent.TYPE_REVOKE:
                log.info("流程撤销：instanceId={}, handler={}", event.getInstanceId(), event.getHandler());
                break;
            case FlowEvent.TYPE_COMPLETE:
                log.info("流程完成：instanceId={}, businessId={}", event.getInstanceId(), event.getBusinessId());
                break;
            case FlowEvent.TYPE_RESTART:
                log.info("流程重启：instanceId={}, businessId={}", event.getInstanceId(), event.getBusinessId());
                break;
            case FlowEvent.TYPE_TIMEOUT:
                log.info("流程超时处理：instanceId={}, message={}", event.getInstanceId(), event.getMessage());
                break;
            default:
                break;
        }
    }
}