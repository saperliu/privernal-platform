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

package com.privernal.flow.handler;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.flow.constant.FlowConstant;
import com.privernal.flow.notice.FlowNoticeMessage;
import com.privernal.flow.notice.FlowNoticeSender;
import com.privernal.flow.service.IFlowUserResolveService;
import com.privernal.flow.utils.FlowNodeExtUtil;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.SkipType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 抄送节点处理器
 * <p>
 * 设计器抄送节点保存为普通中间节点（ext.nodeKind=cc，permissionFlag=抄送人员）。
 * 流程到达抄送节点时：给抄送人员发通知，然后自动通过该节点使流程继续流转，
 * 抄送人可在已办/抄送列表看到该记录。
 * </p>
 *
 * @author saperliu
 */
@Component
public class FlowCcTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(FlowCcTaskHandler.class);

    /** 连续抄送节点自动跳过的最大次数（防互为抄送导致死循环） */
    private static final int MAX_CC_SKIP_ROUNDS = 20;

    private final ObjectProvider<FlowNoticeSender> noticeSenderProvider;

    private final IFlowUserResolveService flowUserResolveService;

    public FlowCcTaskHandler(ObjectProvider<FlowNoticeSender> noticeSenderProvider,
                             IFlowUserResolveService flowUserResolveService) {
        this.noticeSenderProvider = noticeSenderProvider;
        this.flowUserResolveService = flowUserResolveService;
    }

    /**
     * 自动跳过实例当前停留的抄送节点（通知抄送人后自动通过）
     * 非抄送节点不受影响；连跳多轮直到当前节点为普通审批节点或流程结束
     */
    public void autoSkipCcNodes(Long instanceId) {
        if (instanceId == null) {
            return;
        }
        for (int round = 0; round < MAX_CC_SKIP_ROUNDS; round++) {
            List<Task> tasks = FlowEngine.taskService().getByInsId(instanceId);
            if (tasks == null || tasks.isEmpty()) {
                return;
            }
            boolean skipped = false;
            for (Task task : tasks) {
                Node node = findNode(task);
                if (!FlowNodeExtUtil.isCcNode(node)) {
                    continue;
                }
                notifyCcUsers(task, node);
                try {
                    // ignore(true)：系统自动操作跳过办理人权限校验（无登录态）
                    FlowParams params = FlowParams.build()
                            .ignore(true)
                            .skipType(SkipType.PASS.getKey())
                            .message("抄送节点自动通过");
                    FlowEngine.taskService().skip(task.getId(), params);
                    skipped = true;
                } catch (Exception e) {
                    // 单个抄送节点失败不阻断主流程
                    log.warn("抄送节点自动通过失败 taskId={} nodeCode={}", task.getId(), task.getNodeCode(), e);
                }
            }
            if (!skipped) {
                return;
            }
        }
    }

    /**
     * 通知抄送人员（节点权限标识解析为具体用户后发送）
     */
    private void notifyCcUsers(Task task, Node node) {
        FlowNoticeSender sender = noticeSenderProvider.getIfAvailable();
        if (sender == null || StringUtils.isEmpty(node.getPermissionFlag())) {
            return;
        }
        try {
            List<String> flags = Arrays.stream(node.getPermissionFlag().split(FlowConstant.PERMISSION_FLAG_SEPARATOR))
                    .filter(StringUtils::isNotEmpty)
                    .toList();
            List<String> userIds = flowUserResolveService.convertPermissions(new ArrayList<>(flags));
            if (userIds.isEmpty()) {
                return;
            }
            FlowNoticeMessage notice = new FlowNoticeMessage();
            notice.setInstanceId(task.getInstanceId());
            notice.setBusinessId(task.getBusinessId());
            String flowName = StringUtils.isEmpty(task.getFlowName()) ? "流程" : task.getFlowName();
            notice.setTitle("您收到一条流程抄送");
            notice.setContent("【" + flowName + "】流程已抄送给您，请查阅");
            notice.setUserIds(userIds);
            sender.send(notice);
        } catch (Exception e) {
            log.warn("抄送通知发送失败 taskId={}", task.getId(), e);
        }
    }

    /**
     * 按任务定位流程节点
     */
    public Node findNode(Task task) {
        if (task == null || task.getDefinitionId() == null || StringUtils.isEmpty(task.getNodeCode())) {
            return null;
        }
        Node condition = FlowEngine.newNode();
        condition.setDefinitionId(task.getDefinitionId());
        condition.setNodeCode(task.getNodeCode());
        List<Node> nodes = FlowEngine.nodeService().list(condition);
        return nodes == null || nodes.isEmpty() ? null : nodes.get(0);
    }
}
