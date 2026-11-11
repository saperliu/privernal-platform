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

package com.privernal.flow.service.impl;

import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.flow.context.FlowContextHolder;
import com.privernal.flow.domain.FlowFormBind;
import com.privernal.flow.domain.FlowSubflowRel;
import com.privernal.flow.dto.FlowInstanceStatusDTO;
import com.privernal.flow.dto.FlowNodeInfoDTO;
import com.privernal.flow.dto.FlowStartDTO;
import com.privernal.flow.filter.FlowInstanceFilterDTO;
import com.privernal.flow.handler.FlowCcTaskHandler;
import com.privernal.flow.handler.FlowNoHandlerTaskHandler;
import com.privernal.flow.handler.FlowTimeoutTaskSyncer;
import com.privernal.flow.mapper.FlowQueryMapper;
import com.privernal.flow.mapper.FlowSubflowRelMapper;
import com.privernal.flow.listener.FlowEventPublisher;
import com.privernal.flow.notice.FlowNoticeMessage;
import com.privernal.flow.notice.FlowNoticeSender;
import com.privernal.flow.dto.FlowFormVersionDTO;
import com.privernal.flow.service.IFlowFormService;
import com.privernal.flow.service.IFlowFormVersionService;
import com.privernal.flow.service.IFlowInstanceService;
import com.privernal.flow.service.IFlowUserResolveService;
import com.privernal.flow.utils.FlowNodeExtUtil;
import com.privernal.dynamic.feign.RemoteDynamicService;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程实例服务实现
 *
 * @author saperliu
 */
@Service
public class FlowInstanceServiceImpl implements IFlowInstanceService {

    private static final Logger log = LoggerFactory.getLogger(FlowInstanceServiceImpl.class);

    /** warm-flow 流程状态：已通过 */
    private static final String FLOW_STATUS_PASS = "2";
    /** warm-flow 流程状态：终止 */
    private static final String FLOW_STATUS_TERMINATE = "4";
    /** warm-flow 流程状态：作废 */
    private static final String FLOW_STATUS_NULLIFY = "5";
    /** warm-flow 流程状态：撤销 */
    private static final String FLOW_STATUS_CANCEL = "6";
    /** warm-flow 流程状态：已完成 */
    private static final String FLOW_STATUS_FINISHED = "8";
    /** warm-flow 流程状态：已退回 */
    private static final String FLOW_STATUS_REJECT = "9";
    /** warm-flow 流程状态：失效 */
    private static final String FLOW_STATUS_INVALID = "10";

    /** 节点类型：开始节点（退回到发起节点时业务数据回到草稿态） */
    private static final int NODE_TYPE_START = 0;

    /** R2-18：子流程嵌套深度上限（防 A→B→A 引用环与失控递归，链上任一层超限即拒绝发起） */
    private static final int MAX_SUBFLOW_DEPTH = 3;

    /** R2-18：子流程实例合成 businessId 前缀（不走动态表单绑定/状态回写链路） */
    private static final String SUBFLOW_BUSINESS_PREFIX = "subflow-";

    @Autowired
    private FlowQueryMapper flowQueryMapper;

    @Autowired
    private FlowSubflowRelMapper flowSubflowRelMapper;

    /** 子流程通知发送器：调度/系统线程可能没有实现，ObjectProvider 容忍缺省 */
    @Autowired
    private ObjectProvider<FlowNoticeSender> noticeSenderProvider;

    @Autowired
    private IFlowFormService flowFormService;

    @Autowired
    private IFlowFormVersionService flowFormVersionService;

    @Autowired
    private IFlowUserResolveService flowUserResolveService;

    @Autowired
    private RemoteDynamicService remoteDynamicService;

    @Autowired
    private FlowEventPublisher flowEventPublisher;

    @Autowired
    private FlowCcTaskHandler ccTaskHandler;

    @Autowired
    private FlowNoHandlerTaskHandler noHandlerTaskHandler;

    @Autowired
    private FlowTimeoutTaskSyncer timeoutTaskSyncer;

    @Autowired
    private com.privernal.system.feign.RemoteUserService remoteUserService;

    @Override
    @DS("master")
    public FlowInstanceStatusDTO start(FlowStartDTO startDTO) {
        if (StringUtils.isEmpty(startDTO.getBusinessId())) {
            throw new ServiceException("业务数据id不能为空");
        }
        String flowCode = startDTO.getFlowCode();
        FlowFormBind bind = null;
        if (StringUtils.isEmpty(flowCode)) {
            if (StringUtils.isEmpty(startDTO.getTableCode())) {
                throw new ServiceException("flowCode 与 tableCode 至少传一个");
            }
            bind = flowFormService.findBindByTableCode(startDTO.getTableCode());
            if (bind != null) {
                flowCode = bind.getFlowCode();
            } else {
                // 旧绑定表未配置时回退版本表（flow_form_version 当前生效发布版）
                FlowFormVersionDTO version = flowFormVersionService.findCurrentPublishedByTableCode(startDTO.getTableCode());
                if (version == null) {
                    throw new ServiceException("表单未绑定流程：" + startDTO.getTableCode());
                }
                flowCode = version.getFlowCode();
            }
        }
        Definition definition = FlowEngine.defService().getPublishByFlowCode(flowCode);
        if (definition == null) {
            throw new ServiceException("流程定义不存在或未发布：" + flowCode);
        }
        // 幂等：同一业务数据不允许并发多个运行中流程
        List<FlowInstance> running = flowQueryMapper.selectRunningInstanceByBusinessId(startDTO.getBusinessId());
        if (running != null && !running.isEmpty()) {
            throw new ServiceException("该业务数据已有运行中的流程实例");
        }
        String handler = StringUtils.isNotEmpty(startDTO.getHandler())
                ? startDTO.getHandler() : SecurityUtils.getUserId();
        if (StringUtils.isEmpty(handler)) {
            throw new ServiceException("无法确定流程发起人");
        }
        FlowContextHolder.FlowContext context = new FlowContextHolder.FlowContext();
        context.setInitiatorId(handler);
        String contextOrganizationId = SecurityUtils.getOrganizationId();
        if (StringUtils.isEmpty(contextOrganizationId)) {
            // 机器调用（OpenAPI）没有登录态，组织由验签过滤器写进数据范围上下文
            contextOrganizationId = OrganizationContextHolder.getOrganizationId();
        }
        context.setOrganizationId(contextOrganizationId);
        context.setVariables(startDTO.getVariables() == null ? new HashMap<>() : startDTO.getVariables());
        FlowContextHolder.set(context);
        try {
            FlowParams params = FlowParams.build()
                    .flowCode(flowCode)
                    .handler(handler)
                    .variable(context.getVariables());
            Instance instance = FlowEngine.insService().start(startDTO.getBusinessId(), params);
            // 流程标题：按「发起人姓名的流程名」格式填写（如 张三的请假条），同步到实例与待办任务，
            // 后续节点任务/历史记录由 FlowDataFillHandler 继承实例标题
            try {
                applyInstanceTitle(instance, definition.getFlowName(), handler);
            } catch (Exception e) {
                log.warn("流程标题格式化失败 instanceId={}", instance.getId(), e);
            }
            syncBusinessStatus(instance.getId());
            // 发起后第一节点若为抄送节点，自动通过并通知抄送人
            try {
                ccTaskHandler.autoSkipCcNodes(instance.getId());
                noHandlerTaskHandler.handleUnresolvedNodes(instance.getId());
                // 自动跳过可能推进流程结束，补一次状态回写防止单据停在「审批中」
                syncBusinessStatus(instance.getId());
            } catch (Exception e) {
                log.warn("发起后抄送节点自动跳过失败 instanceId={}", instance.getId(), e);
            }
            // R2-18：发起后第一节点若为子流程节点，立即发起子流程（失败不阻断发起主链路）
            try {
                launchSubflowTasks(instance.getId());
            } catch (Exception e) {
                log.warn("发起后子流程节点处理失败 instanceId={}", instance.getId(), e);
            }
            // 发起即注册首节点超时任务（§10.2 调用点）：此前仅转办/委派/加签/减签等动作
            // 触发注册，正常流转与首节点的超时只能靠每日兜底扫描，处置严重滞后
            try {
                timeoutTaskSyncer.syncByInstanceAsync(instance.getId());
            } catch (Exception e) {
                log.warn("发起后超时任务注册失败 instanceId={}", instance.getId(), e);
            }
            flowEventPublisher.publishStart(instance);
            return toStatusDTO(instance);
        } finally {
            FlowContextHolder.clear();
        }
    }

    /**
     * 流程标题格式化：发起人姓名 + "的" + 流程名（如 张三的请假条）。
     * 回写实例标题，并同步到该实例当前的待办任务（后续任务由 FlowDataFillHandler 继承）
     */
    private void applyInstanceTitle(Instance instance, String definitionFlowName, String handlerUserId) {
        String nickName = null;
        try {
            var userResult = remoteUserService.findUserById(handlerUserId, "");
            if (userResult != null && userResult.getData() != null) {
                nickName = userResult.getData().getNickName();
            }
        } catch (Exception e) {
            log.warn("查询发起人姓名失败 handler={}", handlerUserId);
        }
        if (StringUtils.isEmpty(nickName)) {
            nickName = handlerUserId;
        }
        String title = nickName + "的" + definitionFlowName;
        instance.setFlowName(title);
        // 直接 SQL 回写（warm-flow 实体 updateById 对 flow_name 不生效）
        flowQueryMapper.updateInstanceFlowName(instance.getId(), title);
        flowQueryMapper.updateTaskFlowNameByInstance(instance.getId(), title);
        List<Task> tasks = FlowEngine.taskService().list(FlowEngine.newTask()
                .setInstanceId(instance.getId()));
        for (Task task : tasks) {
            task.setFlowName(title);
        }
    }

    @Override
    @DS("master")
    public Page<Instance> pageInstances(FlowInstanceFilterDTO filter, boolean onlyMine) {
        int pageNum = filter.getPageNum() == null ? 1 : filter.getPageNum();
        int pageSize = filter.getPageSize() == null ? 10 : filter.getPageSize();
        Page<FlowInstance> page = new Page<>(pageNum, pageSize);
        String createBy = onlyMine ? SecurityUtils.getUserId() : null;
        return (Page<Instance>) (Page) flowQueryMapper.selectInstancePage(page, createBy,
                filter.getFlowCode(), filter.getFlowName(), filter.getBusinessId(), filter.getFlowStatus(),
                filter.getTimeStart(), filter.getTimeEnd());
    }

    @Override
    @DS("master")
    public Instance getByBusinessId(String businessId) {
        return flowQueryMapper.selectLastInstanceByBusinessId(businessId);
    }

    @Override
    @DS("master")
    public FlowInstanceStatusDTO getStatusByBusinessId(String businessId) {
        Instance instance = getByBusinessId(businessId);
        if (instance == null) {
            return null;
        }
        return toStatusDTO(instance);
    }

    @Override
    @DS("master")
    public boolean isCurrentUserParticipant(String tableCode, String businessId) {
        if (StringUtils.isEmpty(tableCode) || StringUtils.isEmpty(businessId)) {
            return false;
        }
        Instance instance = getByBusinessId(businessId);
        if (instance == null) {
            return false;
        }
        // 参与身份只对被引用的那张表生效：businessId 是各表自己的主键，
        // 不校验表编码会让「参与了 A 表流程」换成「读取 B 表同 id 数据」的权限
        Definition definition = instance.getDefinitionId() == null ? null
                : FlowEngine.defService().getById(instance.getDefinitionId());
        String instanceTableCode = resolveTableCode(definition == null ? null : definition.getFlowCode());
        if (!tableCode.equals(instanceTableCode)) {
            return false;
        }
        return checkCurrentUserIsParticipant(instance);
    }

    /**
     * 参与人判定：当前待办人（可办理）之外，流程发起人、曾任办理人/协办人同样要能看到自己经手的申请单
     */
    private boolean checkCurrentUserIsParticipant(Instance instance) {
        String userId = SecurityUtils.getUserId();
        if (StringUtils.isEmpty(userId)) {
            return false;
        }
        if (userId.equals(instance.getCreateBy())) {
            return true;
        }
        if (flowFormService.isCurrentUserHandler(instance)) {
            return true;
        }
        List<String> flags = flowUserResolveService.expandCurrentUserFlags();
        List<HisTask> hisTasks = FlowEngine.hisTaskService().getByInsId(instance.getId());
        if (hisTasks == null) {
            return false;
        }
        for (HisTask hisTask : hisTasks) {
            if (flags.contains(hisTask.getApprover()) || flags.contains(hisTask.getCollaborator())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @DS("master")
    public FlowInstanceStatusDTO getStatusByInstanceId(Long instanceId) {
        Instance instance = FlowEngine.insService().getById(instanceId);
        if (instance == null) {
            return null;
        }
        return toStatusDTO(instance);
    }

    @Override
    @DS("master")
    public FlowInstanceStatusDTO terminate(Long instanceId, String message) {
        return terminate(instanceId, message, null);
    }

    @Override
    @DS("master")
    public FlowInstanceStatusDTO terminate(Long instanceId, String message, String handler) {
        Instance instance = FlowEngine.insService().getById(instanceId);
        if (instance == null) {
            throw new ServiceException("流程实例不存在：" + instanceId);
        }
        FlowParams params = FlowParams.build()
                .handler(StringUtils.isEmpty(handler) ? SecurityUtils.getUserId() : handler)
                .message(StringUtils.isEmpty(message) ? "终止流程" : message);
        // R2-18：父流程终止时级联终止其发起中的子流程，避免子流程成为无主实例继续流转
        cascadeTerminateSubflows(instanceId, params.getHandler());
        FlowEngine.taskService().terminationByInsId(instanceId, params);
        syncBusinessStatus(instanceId);
        Instance latest = FlowEngine.insService().getById(instanceId);
        flowEventPublisher.publishTerminate(latest, params.getMessage());
        return toStatusDTO(latest);
    }

    @Override
    @DS("master")
    public FlowInstanceStatusDTO revoke(Long instanceId, String message) {
        Instance instance = FlowEngine.insService().getById(instanceId);
        if (instance == null) {
            throw new ServiceException("流程实例不存在：" + instanceId);
        }
        String userId = SecurityUtils.getUserId();
        if (!userId.equals(instance.getCreateBy()) && !SecurityUtils.isSuperRoleUser()) {
            throw new ServiceException("只有发起人可以撤销流程");
        }
        FlowParams params = FlowParams.build()
                .handler(userId)
                .message(StringUtils.isEmpty(message) ? "撤销流程" : message);
        FlowEngine.taskService().revoke(instanceId, params);
        // R2-18：父流程撤销同样级联终止发起中的子流程
        cascadeTerminateSubflows(instanceId, userId);
        syncBusinessStatus(instanceId);
        Instance latest = FlowEngine.insService().getById(instanceId);
        flowEventPublisher.publishRevoke(latest, params.getMessage());
        return toStatusDTO(latest);
    }

    @Override
    @DS("master")
    public boolean active(Long instanceId) {
        return FlowEngine.insService().active(instanceId);
    }

    @Override
    @DS("master")
    public boolean unActive(Long instanceId) {
        return FlowEngine.insService().unActive(instanceId);
    }

    @Override
    @DS("master")
    public List<HisTask> records(Long instanceId) {
        List<HisTask> list = FlowEngine.hisTaskService().getByInsId(instanceId);
        return list;
    }

    @Override
    @DS("master")
    public Definition getDefinitionByBusinessId(String businessId) {
        Instance instance = getByBusinessId(businessId);
        if (instance == null) {
            return null;
        }
        return FlowEngine.defService().getById(instance.getDefinitionId());
    }

    /**
     * 同步业务数据状态：
     * warm-flow 状态 -> 业务数据 status（WorkflowDataStatus）
     * 2/3/8 通过或完成 -> 1 已完成；4/5/10 终止/作废/失效 -> 3 已取消；
     * 6 撤销 -> 0 草稿；9 已退回时，退回到发起节点则 0 草稿（可编辑重提），否则保持 2 运行中
     */
    @Override
    @DS("master")
    public void syncBusinessStatus(Long instanceId) {
        try {
            Instance instance = FlowEngine.insService().getById(instanceId);
            if (instance == null || StringUtils.isEmpty(instance.getBusinessId())) {
                return;
            }
            Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
            String flowCode = definition == null ? null : definition.getFlowCode();
            if (StringUtils.isEmpty(flowCode)) {
                return;
            }
            String tableCode = resolveTableCode(flowCode);
            if (StringUtils.isEmpty(tableCode)) {
                return;
            }
            Integer dataStatus = resolveDataStatus(instance);
            if (dataStatus == null) {
                return;
            }
            try {
                boolean success = remoteDynamicService.updateBusinessStatus(tableCode, instance.getBusinessId(), dataStatus);
                if (!success) {
                    log.warn("流程业务状态回写未成功 tableCode={} businessId={} status={}",
                            tableCode, instance.getBusinessId(), dataStatus);
                }
            } catch (Exception e) {
                // 状态回写失败不阻塞流程主链路，记录日志人工补偿
                log.error("流程业务状态回写失败 tableCode={} businessId={} status={}",
                        tableCode, instance.getBusinessId(), dataStatus, e);
            }
        } catch (Exception e) {
            log.error("同步流程业务状态失败 instanceId={}", instanceId, e);
        }
    }

    /**
     * 按流程编码解析业务表编码：flow_form_bind 优先，未配置时回退当前生效的流程表单发布版
     * （flow_form_version），仍取不到时按流程码反查 dynamic_form.workflow_code
     * （表单只在设计器里绑定了流程、没有版本配置记录）
     */
    private String resolveTableCode(String flowCode) {
        if (StringUtils.isEmpty(flowCode)) {
            return null;
        }
        FlowFormBind bind = flowFormService.findBindByFlowCode(flowCode);
        if (bind != null && StringUtils.isNotEmpty(bind.getTableCode())) {
            return bind.getTableCode();
        }
        FlowFormVersionDTO version = flowFormVersionService.findCurrentPublishedByFlowCode(flowCode);
        String tableCode = version == null ? null : version.getTableCode();
        if (StringUtils.isEmpty(tableCode)) {
            tableCode = remoteDynamicService.findTableCodeByFlowCode(flowCode);
        }
        return StringUtils.isEmpty(tableCode) ? null : tableCode;
    }

    private Integer resolveDataStatus(Instance instance) {
        String flowStatus = instance.getFlowStatus();
        if (StringUtils.isEmpty(flowStatus)) {
            return WorkflowDataStatus.PROCESS.getCode().intValue();
        }
        switch (flowStatus) {
            case FLOW_STATUS_PASS:
            case FLOW_STATUS_FINISHED:
                return WorkflowDataStatus.FINISH.getCode().intValue();
            case FLOW_STATUS_TERMINATE:
            case FLOW_STATUS_NULLIFY:
            case FLOW_STATUS_INVALID:
                return WorkflowDataStatus.CANCEL.getCode().intValue();
            case FLOW_STATUS_CANCEL:
                return WorkflowDataStatus.STAGING.getCode().intValue();
            case FLOW_STATUS_REJECT:
                // 退回到发起节点回到草稿态，退回到中间节点保持运行中
                if (instance.getNodeType() != null && instance.getNodeType() == NODE_TYPE_START) {
                    return WorkflowDataStatus.STAGING.getCode().intValue();
                }
                return WorkflowDataStatus.PROCESS.getCode().intValue();
            default:
                return WorkflowDataStatus.PROCESS.getCode().intValue();
        }
    }

    private FlowInstanceStatusDTO toStatusDTO(Instance instance) {
        FlowInstanceStatusDTO dto = new FlowInstanceStatusDTO();
        dto.setInstanceId(instance.getId());
        dto.setBusinessId(instance.getBusinessId());
        dto.setFlowStatus(instance.getFlowStatus());
        dto.setNodeCode(instance.getNodeCode());
        dto.setNodeName(instance.getNodeName());
        dto.setRunning(dto.getFlowStatus() != null && isRunning(dto.getFlowStatus()));
        Definition definition = instance.getDefinitionId() == null ? null
                : FlowEngine.defService().getById(instance.getDefinitionId());
        dto.setFlowCode(definition == null ? null : definition.getFlowCode());
        dto.setTableCode(resolveTableCode(dto.getFlowCode()));
        dto.setDataStatus(resolveDataStatus(instance));
        return dto;
    }

    private boolean isRunning(String flowStatus) {
        switch (flowStatus) {
            case FLOW_STATUS_PASS:
            case FLOW_STATUS_TERMINATE:
            case FLOW_STATUS_NULLIFY:
            case FLOW_STATUS_CANCEL:
            case FLOW_STATUS_FINISHED:
            case FLOW_STATUS_INVALID:
                return false;
            default:
                return true;
        }
    }

    @Override
    @DS("master")
    public FlowInstanceStatusDTO restart(Long instanceId, String message) {
        Instance oldInstance = FlowEngine.insService().getById(instanceId);
        if (oldInstance == null) {
            throw new ServiceException("流程实例不存在：" + instanceId);
        }
        Definition definition = FlowEngine.defService().getById(oldInstance.getDefinitionId());
        if (definition == null) {
            throw new ServiceException("流程定义不存在，无法重启");
        }
        String flowCode = definition.getFlowCode();
        String businessId = oldInstance.getBusinessId();
        if (StringUtils.isEmpty(flowCode) || StringUtils.isEmpty(businessId)) {
            throw new ServiceException("流程实例数据异常，无法重启");
        }
        if (isRunning(oldInstance.getFlowStatus())) {
            throw new ServiceException("流程正在运行中，无需重启");
        }
        String handler = oldInstance.getCreateBy();
        log.info("重启流程实例 instanceId={}, flowCode={}, businessId={}", instanceId, flowCode, businessId);
        FlowStartDTO startDTO = new FlowStartDTO();
        startDTO.setFlowCode(flowCode);
        startDTO.setBusinessId(businessId);
        startDTO.setHandler(handler);
        FlowInstanceStatusDTO result = start(startDTO);
        // R2-18：重启的是子流程实例时，把父子关系指到新实例，父流程继续等待新子流程
        rebindSubflowRel(instanceId, result.getInstanceId());
        flowEventPublisher.publishRestart(FlowEngine.insService().getById(result.getInstanceId()));
        return result;
    }

    @Override
    @DS("master")
    public List<FlowNodeInfoDTO> getInstanceNodes(Long instanceId) {
        Instance instance = FlowEngine.insService().getById(instanceId);
        if (instance == null) {
            throw new ServiceException("流程实例不存在：" + instanceId);
        }
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        if (definition == null) {
            throw new ServiceException("流程定义不存在");
        }
        List<Node> allNodes = FlowEngine.nodeService().list(FlowEngine.newNode().setDefinitionId(instance.getDefinitionId()));
        List<HisTask> hisTasks = FlowEngine.hisTaskService().getByInsId(instanceId);
        List<Task> currentTasks = FlowEngine.taskService().getByInsId(instanceId);
        Map<String, HisTask> hisTaskMap = new HashMap<>();
        if (hisTasks != null) {
            for (HisTask ht : hisTasks) {
                if (ht.getNodeCode() != null) {
                    hisTaskMap.put(ht.getNodeCode(), ht);
                }
            }
        }
        Map<String, Task> currentTaskMap = new HashMap<>();
        if (currentTasks != null) {
            for (Task t : currentTasks) {
                if (t.getNodeCode() != null) {
                    currentTaskMap.put(t.getNodeCode(), t);
                }
            }
        }
        List<FlowNodeInfoDTO> result = new ArrayList<>();
        if (allNodes != null) {
            for (Node node : allNodes) {
                FlowNodeInfoDTO dto = new FlowNodeInfoDTO();
                dto.setNodeCode(node.getNodeCode());
                dto.setNodeName(node.getNodeName());
                dto.setNodeType(node.getNodeType());
                Task currentTask = currentTaskMap.get(node.getNodeCode());
                HisTask hisTask = hisTaskMap.get(node.getNodeCode());
                if (currentTask != null) {
                    dto.setNodeStatus("ACTIVE");
                } else if (hisTask != null) {
                    dto.setNodeStatus("COMPLETED");
                } else {
                    dto.setNodeStatus("PENDING");
                }
                result.add(dto);
            }
        }
        return result;
    }

    // ==================== R2-18 子流程桥接 ====================

    /**
     * 发起本实例当前停留的子流程节点（ext.nodeKind=subflow）对应的子流程实例。
     * 幂等：同一父任务只发一次（先查关系行，uk_parent_task 唯一约束兜底并发）；
     * 单节点失败只记日志并通知父流程发起人，不阻断主流程。
     */
    @Override
    @DS("master")
    public void launchSubflowTasks(Long instanceId) {
        if (instanceId == null) {
            return;
        }
        List<Task> tasks = FlowEngine.taskService().getByInsId(instanceId);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (Task task : tasks) {
            try {
                Node node = ccTaskHandler.findNode(task);
                if (!FlowNodeExtUtil.isSubflowNode(node)) {
                    continue;
                }
                String subflowCode = FlowNodeExtUtil.parseExt(node.getExt()).get(FlowNodeExtUtil.KEY_SUBFLOW_CODE);
                if (StringUtils.isEmpty(subflowCode)) {
                    log.warn("子流程节点未配置目标流程编码，任务停留等待人工处理 taskId={} nodeCode={}",
                            task.getId(), task.getNodeCode());
                    continue;
                }
                Long exists = flowSubflowRelMapper.selectCount(new LambdaQueryWrapper<FlowSubflowRel>()
                        .eq(FlowSubflowRel::getParentTaskId, String.valueOf(task.getId())));
                if (exists != null && exists > 0) {
                    continue;
                }
                launchSubflowForTask(task, subflowCode);
            } catch (Exception e) {
                log.error("子流程发起失败，父任务停留等待 taskId={} nodeCode={}", task.getId(), task.getNodeCode(), e);
            }
        }
    }

    /**
     * 单个父任务的子流程发起：校验已发布/防环 → 先插关系行 → 引擎发起 → 回填子实例 id；
     * 发起失败删行允许下次重试，并通知父流程发起人。
     */
    private void launchSubflowForTask(Task task, String subflowCode) {
        Long parentInstanceId = task.getInstanceId();
        Instance parent = FlowEngine.insService().getById(parentInstanceId);
        if (parent == null) {
            return;
        }
        if (createsSubflowCycle(subflowCode, parentInstanceId, 0)) {
            log.error("子流程发起被拒绝（检测到流程引用环或超深嵌套） parentInstanceId={} subflowCode={}",
                    parentInstanceId, subflowCode);
            notifyParentInitiator(parent, subflowCode,
                    "子流程「" + subflowCode + "」发起被拒绝：检测到流程引用环或嵌套超过 " + MAX_SUBFLOW_DEPTH + " 层");
            return;
        }
        Definition childDef = FlowEngine.defService().getPublishByFlowCode(subflowCode);
        if (childDef == null) {
            log.error("子流程没有已发布版本 parentInstanceId={} subflowCode={}", parentInstanceId, subflowCode);
            notifyParentInitiator(parent, subflowCode,
                    "子流程「" + subflowCode + "」没有已发布版本，父任务停留等待，请先发布子流程");
            return;
        }
        // 先插关系行再发起：唯一约束兜底并发双发；实体字段显式赋值（调度线程无登录态，自动填充不生效）
        String initiator = StringUtils.isEmpty(parent.getCreateBy()) ? "system" : parent.getCreateBy();
        FlowSubflowRel rel = new FlowSubflowRel();
        rel.setParentInstanceId(String.valueOf(parentInstanceId));
        rel.setParentTaskId(String.valueOf(task.getId()));
        rel.setSubflowCode(subflowCode);
        rel.setStatus(FlowSubflowRel.STATUS_LAUNCHED);
        rel.setCreatedBy(initiator);
        rel.setCreatedTime(new Date());
        try {
            flowSubflowRelMapper.insert(rel);
        } catch (Exception e) {
            log.warn("子流程关系行写入冲突（可能并发发起，放弃本次） parentTaskId={} subflowCode={}",
                    task.getId(), subflowCode);
            return;
        }
        Instance child;
        try {
            Map<String, Object> variables = parent.getVariableMap() == null ? new HashMap<>() : parent.getVariableMap();
            FlowParams params = FlowParams.build()
                    .flowCode(subflowCode)
                    .handler(initiator)
                    .variable(variables);
            FlowContextHolder.FlowContext context = new FlowContextHolder.FlowContext();
            context.setInitiatorId(initiator);
            context.setVariables(variables);
            FlowContextHolder.set(context);
            try {
                // 合成 businessId：子流程不经动态表单绑定解析与状态回写链路
                child = FlowEngine.insService().start(SUBFLOW_BUSINESS_PREFIX + parentInstanceId + "-" + task.getId(), params);
            } finally {
                FlowContextHolder.clear();
            }
            rel.setChildInstanceId(String.valueOf(child.getId()));
            flowSubflowRelMapper.updateById(rel);
            log.info("子流程已发起 parentInstanceId={} parentTaskId={} childInstanceId={} subflowCode={}",
                    parentInstanceId, task.getId(), child.getId(), subflowCode);
        } catch (Exception e) {
            flowSubflowRelMapper.deleteById(rel.getId());
            log.error("子流程发起失败 parentInstanceId={} subflowCode={}", parentInstanceId, subflowCode, e);
            notifyParentInitiator(parent, subflowCode,
                    "子流程「" + subflowCode + "」发起失败：" + e.getMessage() + "，父任务停留等待");
            return;
        }
        // 子流程首节点若为抄送/无人/子流程节点立即联动；子流程直接走完时回推父流程
        try {
            ccTaskHandler.autoSkipCcNodes(child.getId());
            noHandlerTaskHandler.handleUnresolvedNodes(child.getId());
            launchSubflowTasks(child.getId());
            resumeSubflowParent(child.getId());
        } catch (Exception e) {
            log.warn("子流程发起后联动处理失败 childInstanceId={}", child.getId(), e);
        }
    }

    /**
     * 防环：沿父子关系链向上走，任一祖先实例的流程编码等于待发起的子流程编码即成环；
     * 链长超过 {@link #MAX_SUBFLOW_DEPTH} 同样拒绝（失控递归保险）。
     */
    private boolean createsSubflowCycle(String subflowCode, Long parentInstanceId, int depth) {
        Long cursor = parentInstanceId;
        int level = depth;
        while (cursor != null) {
            if (level > MAX_SUBFLOW_DEPTH) {
                return true;
            }
            Instance inst = FlowEngine.insService().getById(cursor);
            if (inst == null) {
                return false;
            }
            Definition def = FlowEngine.defService().getById(inst.getDefinitionId());
            if (def != null && subflowCode.equals(def.getFlowCode())) {
                return true;
            }
            List<FlowSubflowRel> rels = flowSubflowRelMapper.selectList(new LambdaQueryWrapper<FlowSubflowRel>()
                    .eq(FlowSubflowRel::getChildInstanceId, String.valueOf(cursor)));
            if (rels == null || rels.isEmpty()) {
                return false;
            }
            cursor = Long.valueOf(rels.get(0).getParentInstanceId());
            level++;
        }
        return false;
    }

    /**
     * 本实例（作为子流程）结束后的父流程回推。
     * 办结（通过/走完）→ 自动通过父任务流转并联动抄送/无人/下一级子流程/超时重签/状态回写；
     * 终止/撤销/作废 → 记关系状态并通知父流程发起人（父任务保持等待，人工转办或终止兜底）。
     */
    @Override
    @DS("master")
    public void resumeSubflowParent(Long instanceId) {
        if (instanceId == null) {
            return;
        }
        try {
            List<FlowSubflowRel> rels = flowSubflowRelMapper.selectList(new LambdaQueryWrapper<FlowSubflowRel>()
                    .eq(FlowSubflowRel::getChildInstanceId, String.valueOf(instanceId)));
            if (rels == null || rels.isEmpty()) {
                return;
            }
            FlowSubflowRel rel = rels.get(0);
            if (!FlowSubflowRel.STATUS_LAUNCHED.equals(rel.getStatus())) {
                return;
            }
            Instance child = FlowEngine.insService().getById(instanceId);
            String status = child == null ? null : child.getFlowStatus();
            if (FLOW_STATUS_PASS.equals(status) || FLOW_STATUS_FINISHED.equals(status)) {
                rel.setStatus(FlowSubflowRel.STATUS_DONE);
                rel.setFinishedTime(new Date());
                flowSubflowRelMapper.updateById(rel);
                resumeParentTask(rel, child);
            } else if (FLOW_STATUS_CANCEL.equals(status) || FLOW_STATUS_TERMINATE.equals(status)
                    || FLOW_STATUS_NULLIFY.equals(status) || FLOW_STATUS_INVALID.equals(status)) {
                rel.setStatus(FLOW_STATUS_CANCEL.equals(status)
                        ? FlowSubflowRel.STATUS_CHILD_REVOKED : FlowSubflowRel.STATUS_CHILD_TERMINATED);
                rel.setFinishedTime(new Date());
                flowSubflowRelMapper.updateById(rel);
                Instance parent = FlowEngine.insService().getById(Long.valueOf(rel.getParentInstanceId()));
                if (parent != null) {
                    String action = FLOW_STATUS_CANCEL.equals(status) ? "撤销" : "终止";
                    notifyParentInitiator(parent, rel.getSubflowCode(),
                            "子流程「" + resolveFlowName(child) + "」已" + action + "，父流程停留等待，请人工处理");
                }
            }
        } catch (Exception e) {
            log.error("子流程结束回推父流程失败 childInstanceId={}", instanceId, e);
        }
    }

    /** 子流程办结后系统身份自动通过父任务；后续联动与手动通过（FlowTaskServiceImpl.approve）同口径 */
    private void resumeParentTask(FlowSubflowRel rel, Instance child) {
        Long parentTaskId = Long.valueOf(rel.getParentTaskId());
        Task parentTask = FlowEngine.taskService().getById(parentTaskId);
        if (parentTask == null) {
            log.warn("子流程办结但父任务已不存在（父流程可能已被终止/办结） relId={} parentTaskId={}",
                    rel.getId(), parentTaskId);
            return;
        }
        Instance parent = FlowEngine.insService().getById(parentTask.getInstanceId());
        String handler = parent == null || StringUtils.isEmpty(parent.getCreateBy())
                ? "system" : parent.getCreateBy();
        FlowParams params = FlowParams.build()
                .ignore(true)
                .handler(handler)
                .skipType(SkipType.PASS.getKey())
                .message("子流程「" + resolveFlowName(child) + "」办结，自动流转");
        Instance afterSkip = FlowEngine.taskService().skip(parentTaskId, params);
        if (afterSkip == null) {
            return;
        }
        syncBusinessStatus(afterSkip.getId());
        ccTaskHandler.autoSkipCcNodes(afterSkip.getId());
        noHandlerTaskHandler.handleUnresolvedNodes(afterSkip.getId());
        // 自动跳过可能推进流程结束，补一次状态回写（与 approve 口径一致）
        syncBusinessStatus(afterSkip.getId());
        // 父流程可能停到下一个子流程节点；父实例本身也可能是别人的子流程
        launchSubflowTasks(afterSkip.getId());
        resumeSubflowParent(afterSkip.getId());
        timeoutTaskSyncer.syncByInstanceAsync(afterSkip.getId());
    }

    /** 父流程终止/撤销时级联终止其发起中的子流程，防止子流程成为无主实例继续流转 */
    private void cascadeTerminateSubflows(Long parentInstanceId, String handler) {
        try {
            List<FlowSubflowRel> rels = flowSubflowRelMapper.selectList(new LambdaQueryWrapper<FlowSubflowRel>()
                    .eq(FlowSubflowRel::getParentInstanceId, String.valueOf(parentInstanceId))
                    .eq(FlowSubflowRel::getStatus, FlowSubflowRel.STATUS_LAUNCHED));
            if (rels == null || rels.isEmpty()) {
                return;
            }
            for (FlowSubflowRel rel : rels) {
                if (StringUtils.isEmpty(rel.getChildInstanceId())) {
                    continue;
                }
                try {
                    Long childId = Long.valueOf(rel.getChildInstanceId());
                    FlowParams params = FlowParams.build()
                            .handler(StringUtils.isEmpty(handler) ? "system" : handler)
                            .ignore(true)
                            .message("父流程已结束，子流程级联终止");
                    FlowEngine.taskService().terminationByInsId(childId, params);
                    rel.setStatus(FlowSubflowRel.STATUS_CHILD_TERMINATED);
                    rel.setFinishedTime(new Date());
                    flowSubflowRelMapper.updateById(rel);
                    log.warn("父流程已结束，级联终止子流程 parentInstanceId={} childInstanceId={}",
                            parentInstanceId, childId);
                } catch (Exception e) {
                    log.error("级联终止子流程失败 childInstanceId={}", rel.getChildInstanceId(), e);
                }
            }
        } catch (Exception e) {
            log.error("级联终止子流程查询失败 parentInstanceId={}", parentInstanceId, e);
        }
    }

    /** 重启子流程实例时把父子关系指到新实例（父流程继续等待新子流程） */
    private void rebindSubflowRel(Long oldInstanceId, Long newInstanceId) {
        try {
            List<FlowSubflowRel> rels = flowSubflowRelMapper.selectList(new LambdaQueryWrapper<FlowSubflowRel>()
                    .eq(FlowSubflowRel::getChildInstanceId, String.valueOf(oldInstanceId))
                    .eq(FlowSubflowRel::getStatus, FlowSubflowRel.STATUS_LAUNCHED));
            if (rels == null) {
                return;
            }
            for (FlowSubflowRel rel : rels) {
                rel.setChildInstanceId(String.valueOf(newInstanceId));
                rel.setStatus(FlowSubflowRel.STATUS_LAUNCHED);
                flowSubflowRelMapper.updateById(rel);
                log.info("子流程实例重启，父子关系已指向新实例 oldInstanceId={} newInstanceId={}",
                        oldInstanceId, newInstanceId);
            }
        } catch (Exception e) {
            log.error("重绑子流程关系失败 oldInstanceId={} newInstanceId={}", oldInstanceId, newInstanceId, e);
        }
    }

    @Override
    @DS("master")
    public List<FlowSubflowRel> listSubflowRels(Long instanceId) {
        if (instanceId == null) {
            return new ArrayList<>();
        }
        List<FlowSubflowRel> rels = flowSubflowRelMapper.selectList(new LambdaQueryWrapper<FlowSubflowRel>()
                .eq(FlowSubflowRel::getParentInstanceId, String.valueOf(instanceId))
                .orderByAsc(FlowSubflowRel::getCreatedTime));
        return rels == null ? new ArrayList<>() : rels;
    }

    private void notifyParentInitiator(Instance parent, String subflowCode, String content) {
        try {
            FlowNoticeSender sender = noticeSenderProvider.getIfAvailable();
            if (sender == null || StringUtils.isEmpty(parent.getCreateBy())) {
                return;
            }
            FlowNoticeMessage notice = new FlowNoticeMessage();
            notice.setInstanceId(parent.getId());
            notice.setBusinessId(parent.getBusinessId());
            notice.setFlowName(parent.getFlowName());
            notice.setTitle("子流程提醒：" + (StringUtils.isEmpty(parent.getFlowName()) ? "流程" : parent.getFlowName()));
            notice.setContent(content);
            notice.setUserIds(List.of(parent.getCreateBy()));
            sender.send(notice);
        } catch (Exception e) {
            log.warn("子流程通知父流程发起人失败 parentInstanceId={}", parent.getId(), e);
        }
    }

    private String resolveFlowName(Instance instance) {
        if (instance == null) {
            return "子流程";
        }
        try {
            Definition def = FlowEngine.defService().getById(instance.getDefinitionId());
            return def == null || StringUtils.isEmpty(def.getFlowName()) ? "子流程" : def.getFlowName();
        } catch (Exception e) {
            return "子流程";
        }
    }
}
