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

package com.privernal.flow.notice;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.flow.listener.FlowEvent;
import com.privernal.flow.service.IFlowUserResolveService;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程事件消息通知监听器
 * 审批通过→通知下一节点办理人；驳回/终止/撤销/完成→通知发起人；超时→通知当前办理人。
 * 收件人通过节点权限标识解析为具体用户，实际发送由 FlowNoticeSender 完成。
 *
 * @author saperliu
 */
@Component
public class FlowMessageNotifyListener {

    private static final Logger log = LoggerFactory.getLogger(FlowMessageNotifyListener.class);

    private static final String URL_TODO = "/flow/todo";
    private static final String URL_MY_INSTANCE = "/flow/instance";

    private final ObjectProvider<FlowNoticeSender> noticeSenderProvider;

    private final IFlowUserResolveService flowUserResolveService;

    public FlowMessageNotifyListener(ObjectProvider<FlowNoticeSender> noticeSenderProvider,
                                     IFlowUserResolveService flowUserResolveService) {
        this.noticeSenderProvider = noticeSenderProvider;
        this.flowUserResolveService = flowUserResolveService;
    }

    @Async
    @EventListener
    public void onFlowEvent(FlowEvent event) {
        FlowNoticeSender sender = noticeSenderProvider.getIfAvailable();
        if (sender == null) {
            return;
        }
        try {
            switch (event.getEventType()) {
                case FlowEvent.TYPE_APPROVE:
                    // 通知下一节点办理人（排除操作人自己）
                    List<String> nextHandlers = resolveInstanceHandlers(event.getInstanceId());
                    nextHandlers.removeIf(userId -> userId.equals(event.getHandler()));
                    send(sender, event, nextHandlers, "您有一条新的审批待办", URL_TODO);
                    break;
                case FlowEvent.TYPE_REJECT:
                case FlowEvent.TYPE_TERMINATE:
                case FlowEvent.TYPE_REVOKE:
                case FlowEvent.TYPE_COMPLETE:
                    send(sender, event, resolveInitiator(event.getInstanceId()), "您的流程有新进展", URL_MY_INSTANCE);
                    break;
                case FlowEvent.TYPE_TIMEOUT:
                    send(sender, event, resolveInstanceHandlers(event.getInstanceId()), "流程任务超时提醒", URL_TODO);
                    break;
                default:
                    // START/RESTART/CLAIM 等事件发起人已知，不另行通知
                    break;
            }
        } catch (Exception e) {
            log.warn("流程消息通知处理失败 eventType={}, instanceId={}", event.getEventType(), event.getInstanceId(), e);
        }
    }

    private void send(FlowNoticeSender sender, FlowEvent event, List<String> userIds, String title, String detailUrl) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String flowName = StringUtils.isEmpty(event.getFlowName()) ? "流程" : event.getFlowName();
        FlowNoticeMessage notice = new FlowNoticeMessage();
        notice.setEventType(event.getEventType());
        notice.setInstanceId(event.getInstanceId());
        notice.setBusinessId(event.getBusinessId());
        notice.setFlowCode(event.getFlowCode());
        notice.setFlowName(event.getFlowName());
        notice.setTitle(title);
        notice.setContent(buildContent(event, flowName));
        notice.setDetailUrl(detailUrl);
        notice.setUserIds(userIds);
        sender.send(notice);
        log.info("流程消息通知已发送 eventType={}, instanceId={}, receivers={}", event.getEventType(),
                event.getInstanceId(), userIds);
    }

    private String buildContent(FlowEvent event, String flowName) {
        StringBuilder content = new StringBuilder();
        content.append("【").append(flowName).append("】");
        if (StringUtils.isNotEmpty(event.getBusinessId())) {
            content.append("单据编号 ").append(event.getBusinessId()).append("，");
        }
        switch (event.getEventType()) {
            case FlowEvent.TYPE_APPROVE:
                content.append("待您审批处理");
                break;
            case FlowEvent.TYPE_REJECT:
                content.append("已被驳回，请查看审批意见");
                break;
            case FlowEvent.TYPE_TERMINATE:
                content.append("已被终止，").append(StringUtils.isEmpty(event.getMessage()) ? "" : event.getMessage());
                break;
            case FlowEvent.TYPE_REVOKE:
                content.append("已被发起人撤销");
                break;
            case FlowEvent.TYPE_COMPLETE:
                content.append("已审批完成");
                break;
            case FlowEvent.TYPE_TIMEOUT:
                content.append("任务已超时自动处理，请关注后续节点");
                break;
            default:
                content.append("状态已更新");
                break;
        }
        return content.toString();
    }

    /**
     * 解析实例当前节点办理人（节点权限标识→具体用户id）
     */
    private List<String> resolveInstanceHandlers(Long instanceId) {
        List<Task> tasks = FlowEngine.taskService().getByInsId(instanceId);
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        List<User> users = FlowEngine.userService().getByAssociateds(taskIds,
                UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        Set<String> flags = new LinkedHashSet<>();
        if (users != null) {
            for (User user : users) {
                if (StringUtils.isNotEmpty(user.getProcessedBy())) {
                    flags.add(user.getProcessedBy());
                }
            }
        }
        return flowUserResolveService.convertPermissions(new ArrayList<>(flags));
    }

    /**
     * 解析流程发起人
     */
    private List<String> resolveInitiator(Long instanceId) {
        Instance instance = FlowEngine.insService().getById(instanceId);
        if (instance == null || StringUtils.isEmpty(instance.getCreateBy())) {
            return new ArrayList<>();
        }
        return List.of(instance.getCreateBy());
    }
}
