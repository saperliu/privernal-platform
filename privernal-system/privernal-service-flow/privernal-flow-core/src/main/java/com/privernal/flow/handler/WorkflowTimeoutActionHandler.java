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

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.flow.context.FlowContextHolder;
import com.privernal.flow.listener.FlowEventPublisher;
import com.privernal.flow.service.IFlowInstanceService;
import com.privernal.flow.utils.FlowNodeExtUtil;
import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.SkipType;
import org.springframework.stereotype.Component;

/**
 * WORKFLOW_TIMEOUT 动作：warm-flow 节点超时处置。
 *
 * <p>由调度内核在到期时触发（一次性任务），处置前以引擎当前状态复核待办仍在原节点——
 * 任务已办理/换节点则静默成功并记 SKIPPED 摘要（动作可重入要求）。保持既有差异：
 * 自动通过走 ignore(true)+PASS 并联动抄送/无人处理，自动驳回走 rejectLast 且不调这两步
 * （原 FlowTaskServiceImpl.timeoutAutoPass/timeoutAutoReject 逻辑迁入，每日兜底扫描复用同一路径）。</p>
 *
 * <p>NOTIFY 语义为"只提醒一次"（§10.3 产品已确认）：发完即 DONE，持续催办由业务方
 * 注册周期任务并在办结时取消。</p>
 *
 * <p>action_config: { action: AUTO_PASS|AUTO_REJECT|NOTIFY }
 * biz_context: { instanceId, taskId, nodeCode }</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTimeoutActionHandler implements ScheduleActionHandler {

    public static final String ACTION_PASS = FlowNodeExtUtil.TIMEOUT_AUTO_PASS;
    public static final String ACTION_REJECT = FlowNodeExtUtil.TIMEOUT_AUTO_REJECT;
    public static final String ACTION_NOTIFY = FlowNodeExtUtil.TIMEOUT_NOTIFY;

    private final FlowCcTaskHandler ccTaskHandler;
    private final FlowNoHandlerTaskHandler noHandlerTaskHandler;
    private final IFlowInstanceService flowInstanceService;
    private final FlowEventPublisher flowEventPublisher;
    private final FlowTimeoutTaskSyncer timeoutTaskSyncer;

    @Override
    public String actionType() {
        return "WORKFLOW_TIMEOUT";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        JSONObject biz = parseBiz(task.getBizContext());
        Long flowTaskId = biz.getLong("taskId");
        String nodeCode = biz.getStr("nodeCode");
        if (flowTaskId == null) {
            return ActionResult.fail("biz_context 缺少 taskId");
        }
        // ===== 处置前以引擎当前状态复核（§8.1 可重入）=====
        Task flowTask = FlowEngine.taskService().getById(flowTaskId);
        if (flowTask == null) {
            return ActionResult.ok("SKIPPED: 待办已办理或已不存在");
        }
        if (StringUtils.isNotEmpty(nodeCode) && !nodeCode.equals(flowTask.getNodeCode())) {
            return ActionResult.ok("SKIPPED: 待办已流转到其他节点 " + flowTask.getNodeCode());
        }
        String action = str(context.getConfig().get("action"), ACTION_PASS);
        switch (action) {
            case ACTION_REJECT -> {
                timeoutAutoReject(flowTask);
                return ActionResult.ok("AUTO_REJECT done, flowTask=" + flowTaskId);
            }
            case ACTION_NOTIFY -> {
                publishTimeoutNotify(flowTask, "任务超时提醒：请及时办理");
                return ActionResult.ok("NOTIFY sent once, flowTask=" + flowTaskId);
            }
            default -> {
                timeoutAutoPass(flowTask);
                return ActionResult.ok("AUTO_PASS done, flowTask=" + flowTaskId);
            }
        }
    }

    /**
     * 超时自动通过（跳转下一节点）并联动抄送/业务状态；流转后重签本实例超时任务
     * （下一节点可能也配了超时）。每日兜底扫描与调度触发共用本方法。
     */
    public void timeoutAutoPass(Task task) {
        FlowContextHolder.set(buildSystemTaskContext(task));
        try {
            Instance current = FlowEngine.insService().getById(task.getInstanceId());
            FlowParams params = FlowParams.build()
                    .ignore(true)
                    // P0-6：会签/票签节点硬断言 handler 非空，且 setApprover 记历史审批人；
                    // 调度线程无会话，SecurityUtils.getUserId() 返回空串——显式传办理人
                    .handler(resolveTimeoutHandler(task, current))
                    .ignoreCooperate(true)
                    .skipType(SkipType.PASS.getKey())
                    .message("节点超时自动通过，跳转下一节点");
            Instance instance = FlowEngine.taskService().skip(task.getId(), params);
            if (instance != null) {
                flowInstanceService.syncBusinessStatus(instance.getId());
                ccTaskHandler.autoSkipCcNodes(instance.getId());
                noHandlerTaskHandler.handleUnresolvedNodes(instance.getId());
                // 自动跳过可能推进流程结束，补一次状态回写防止单据停在「审批中」
                flowInstanceService.syncBusinessStatus(instance.getId());
                flowEventPublisher.publishTimeout(instance.getId(), resolveFlowCode(instance),
                        instance.getBusinessId(), "任务超时已自动通过，请关注后续节点");
                timeoutTaskSyncer.syncByInstanceAsync(instance.getId());
            }
        } finally {
            FlowContextHolder.clear();
        }
    }

    /**
     * 超时自动驳回（驳回到上一节点），系统操作跳过办理人校验；
     * 刻意不联动抄送/无人处理（保持既有差异，§8.1）
     */
    public void timeoutAutoReject(Task task) {
        FlowContextHolder.set(buildSystemTaskContext(task));
        try {
            FlowParams params = FlowParams.build()
                    .handler(resolveTimeoutHandler(task, null))
                    .ignoreCooperate(true)
                    .ignore(true)
                    .message("节点超时自动驳回");
            Instance instance = FlowEngine.taskService().rejectLast(task.getId(), params);
            if (instance != null) {
                flowInstanceService.syncBusinessStatus(instance.getId());
                flowEventPublisher.publishTimeout(instance.getId(), resolveFlowCode(instance),
                        instance.getBusinessId(), "任务超时已自动驳回");
                timeoutTaskSyncer.syncByInstanceAsync(instance.getId());
            }
        } finally {
            FlowContextHolder.clear();
        }
    }

    /** 只提醒一次（§10.3）：发完即返回，任务由内核置 DONE */
    public void publishTimeoutNotify(Task task, String message) {
        Instance instance = FlowEngine.insService().getById(task.getInstanceId());
        if (instance != null) {
            flowEventPublisher.publishTimeout(instance.getId(), resolveFlowCode(instance),
                    instance.getBusinessId(), message);
        }
    }

    /**
     * P0-6：超时处置的显式办理人——取实例发起人（业务语义：超时由流程发起人代为处置），
     * 取不到时回退任务创建人；两者皆空则<b>抛错</b>（由 max_retry 重试 + §15 告警暴露），
     * 不静默返回空串——空串会让 warm-flow 回落到 {@code FlowPermissionHandler.getHandler →
     * SecurityUtils.getUserId()}，在调度线程里必然抛 SaTokenContextException，处置 100% 失败。
     */
    private String resolveTimeoutHandler(Task task, Instance cached) {
        try {
            Instance instance = cached != null ? cached : FlowEngine.insService().getById(task.getInstanceId());
            if (instance != null && StringUtils.isNotEmpty(instance.getCreateBy())) {
                return instance.getCreateBy();
            }
        } catch (Exception e) {
            log.warn("超时办理人解析失败 taskId={}", task.getId(), e);
        }
        if (StringUtils.isNotEmpty(task.getCreateBy())) {
            return task.getCreateBy();
        }
        throw new IllegalStateException("无法确定超时处置的办理人（实例发起人与任务创建人均为空），taskId="
                + task.getId() + "；请为节点配置 ext.wfsNoFoundUser 兜底或检查流程实例数据");
    }

    /** 定时线程（无登录态）构建流程上下文：从实例反查发起人与组织 */
    private FlowContextHolder.FlowContext buildSystemTaskContext(Task task) {
        FlowContextHolder.FlowContext context = new FlowContextHolder.FlowContext();
        try {
            Instance instance = FlowEngine.insService().getById(task.getInstanceId());
            if (instance == null) {
                return context;
            }
            context.setInitiatorId(instance.getCreateBy());
            context.setVariables(instance.getVariableMap() == null ? new java.util.HashMap<>() : instance.getVariableMap());
            if (StringUtils.isNotEmpty(instance.getCreateBy())) {
                // 组织/办理人解析依赖远程查询；handler 在调度线程运行，FlowTimeoutTaskSyncer
                // 注册时已把发起人组织写进任务行，这里按实例发起人兜底
                var userResult = timeoutTaskSyncer.findUser(instance.getCreateBy());
                if (userResult != null && userResult.getData() != null
                        && StringUtils.isNotEmpty(userResult.getData().getOrganizationId())) {
                    context.setOrganizationId(userResult.getData().getOrganizationId());
                }
            }
        } catch (Exception e) {
            log.warn("构建系统任务上下文失败 taskId={}", task.getId(), e);
        }
        return context;
    }

    private String resolveFlowCode(Instance instance) {
        if (instance == null || instance.getDefinitionId() == null) {
            return null;
        }
        var definition = FlowEngine.defService().getById(instance.getDefinitionId());
        return definition == null ? null : definition.getFlowCode();
    }

    private JSONObject parseBiz(String bizContext) {
        if (StringUtils.isEmpty(bizContext)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(bizContext);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s.isEmpty() ? fallback : s;
    }
}
