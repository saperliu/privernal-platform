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

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.privernal.common.core.utils.StringUtils;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.UserType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程催办消息发送（手动催办与重复催办调度共用）。
 *
 * <p>办理人取任务在办用户（审批/转办/委托），与待办列表口径一致；
 * 消息走 FlowNoticeSender SPI（宿主桥接站内信落库 + 实时推送）。
 * 限频/上限由调用方负责，本类只负责组装与发送。</p>
 *
 * @author saperliu
 */
@Component
public class FlowUrgeNotifier {

    private final ObjectProvider<FlowNoticeSender> noticeSenderProvider;

    public FlowUrgeNotifier(ObjectProvider<FlowNoticeSender> noticeSenderProvider) {
        this.noticeSenderProvider = noticeSenderProvider;
    }

    /**
     * 解析任务在办用户标识（审批/转办/委托三类，去空）
     */
    public List<String> resolveHandlers(Long taskId) {
        List<User> todoUsers = FlowEngine.userService().listByAssociatedAndTypes(taskId,
                UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        List<String> handlers = new ArrayList<>();
        if (todoUsers != null) {
            for (User user : todoUsers) {
                if (StringUtils.isNotEmpty(user.getProcessedBy())) {
                    handlers.add(user.getProcessedBy());
                }
            }
        }
        return handlers;
    }

    /**
     * 发送催办消息（站内信落库 + 实时推送）
     *
     * @param task    待办任务
     * @param comment 催办说明（空取默认文案）
     * @return 实际发送的收件人数
     */
    public int send(Task task, String comment) {
        List<String> handlers = resolveHandlers(task.getId());
        if (handlers.isEmpty()) {
            return 0;
        }
        Instance instance = FlowEngine.insService().getById(task.getInstanceId());
        String flowName = instance == null || StringUtils.isEmpty(instance.getFlowName())
                ? "流程" : instance.getFlowName();
        FlowNoticeMessage notice = new FlowNoticeMessage();
        notice.setEventType("URGE");
        notice.setInstanceId(task.getInstanceId());
        notice.setBusinessId(instance == null ? null : instance.getBusinessId());
        notice.setFlowName(flowName);
        notice.setTitle("催办提醒：" + flowName);
        notice.setContent(StringUtils.isNotEmpty(comment) ? comment : "您的待办任务已收到催办，请尽快处理。");
        notice.setUserIds(handlers);
        noticeSenderProvider.getObject().send(notice);
        return handlers.size();
    }

    /**
     * 调度上下文快照（一次性任务用的 biz_context；周期注册 registerFixedDelay 无此参，
     * WORKFLOW_URGE 的实例/任务定位走 actionConfig）
     */
    public static String buildBizContext(Long instanceId, Long taskId, String nodeCode) {
        JSONObject bizContext = new JSONObject();
        bizContext.set("instanceId", instanceId);
        bizContext.set("taskId", taskId);
        bizContext.set("nodeCode", nodeCode);
        return bizContext.toString();
    }

    /**
     * 调度动作参数快照（WORKFLOW_URGE 的 action_config）：
     * registerFixedDelay 无 bizContext 参数，实例/任务定位随配置快照走；
     * WORKFLOW 来源的任务被调度内核按一次性语义办结（finishOnce），周期催办由
     * handler 自链式续注册（intervalSeconds 随配置携带）。
     */
    public static String buildActionConfig(int maxTimes, Long instanceId, Long taskId, String nodeCode, int intervalSeconds) {
        JSONObject actionConfig = new JSONObject();
        actionConfig.set("maxTimes", maxTimes);
        actionConfig.set("instanceId", instanceId);
        actionConfig.set("taskId", taskId);
        actionConfig.set("nodeCode", nodeCode);
        actionConfig.set("intervalSeconds", intervalSeconds);
        return actionConfig.toString();
    }

    /**
     * 解析调度动作参数（容错：快照缺失按不限处理；兼容数字与 JSON 两种形态）
     */
    public static int parseMaxTimes(Object value) {
        try {
            if (value == null) {
                return 0;
            }
            if (value instanceof Number number) {
                return Math.max(0, number.intValue());
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return 0;
            }
            if (text.matches("\\d+")) {
                return Integer.parseInt(text);
            }
            return Math.max(0, JSONUtil.parseObj(text).getInt("maxTimes", 0));
        } catch (Exception e) {
            return 0;
        }
    }
}
