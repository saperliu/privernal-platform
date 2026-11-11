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

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.flow.constant.FlowConstant;
import com.privernal.flow.context.FlowContextHolder;
import com.privernal.flow.dto.FlowAdjustTaskDTO;
import com.privernal.flow.dto.FlowTaskHandleDTO;
import com.privernal.flow.dto.FlowTaskOptionDTO;
import com.privernal.flow.dto.FlowTodoTaskExtDTO;
import com.privernal.flow.enums.FlowPermissionTypeEnum;
import com.privernal.flow.enums.FlowRejectTypeEnum;
import com.privernal.flow.filter.FlowTaskFilterDTO;
import com.privernal.flow.handler.FlowCcTaskHandler;
import com.privernal.flow.handler.FlowNoHandlerTaskHandler;
import com.privernal.flow.handler.condition.FlowExprConditionStrategy;
import com.privernal.flow.mapper.FlowQueryMapper;
import com.privernal.flow.mapper.FlowRoleNameMapper;
import com.privernal.flow.listener.FlowEventPublisher;
import com.privernal.flow.service.IFlowInstanceService;
import com.privernal.flow.service.IFlowTaskService;
import com.privernal.flow.notice.FlowNoticeMessage;
import com.privernal.flow.notice.FlowNoticeSender;
import com.privernal.flow.service.IFlowUserResolveService;
import com.privernal.flow.utils.FlowNodeExtUtil;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.filter.WorkflowUserFilterDTO;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 流程任务服务实现（审批办理全特性）
 *
 * @author saperliu
 */
@Service
@EnableScheduling
public class FlowTaskServiceImpl implements IFlowTaskService {

    private static final Logger log = LoggerFactory.getLogger(FlowTaskServiceImpl.class);

    /** 超时任务默认超时时间（小时） */
    private static final int DEFAULT_TIMEOUT_HOURS = 48;

    /** warm-flow 流程状态：审批通过（实例走完末节点） */
    private static final String FLOW_STATUS_PASS = "2";

    /** warm-flow 流程状态：已完成（审批通过走完末节点） */
    private static final String FLOW_STATUS_FINISHED = "8";

    @Autowired
    private FlowQueryMapper flowQueryMapper;

    @Autowired
    private FlowRoleNameMapper flowRoleNameMapper;

    @Autowired
    private com.privernal.system.feign.RemoteUserService remoteUserService;

    @Autowired
    private IFlowInstanceService flowInstanceService;

    @Autowired
    private IFlowUserResolveService flowUserResolveService;

    @Autowired
    private FlowEventPublisher flowEventPublisher;

    @Autowired
    private FlowCcTaskHandler ccTaskHandler;

    @Autowired
    private FlowNoHandlerTaskHandler noHandlerTaskHandler;

    @Autowired
    private com.privernal.flow.handler.FlowTimeoutTaskSyncer timeoutTaskSyncer;

    @Autowired
    private com.privernal.flow.handler.WorkflowTimeoutActionHandler workflowTimeoutActionHandler;

    @Autowired
    private org.springframework.beans.factory.ObjectProvider<com.privernal.flow.notice.FlowNoticeSender> noticeSenderProvider;

    /** 催办消息发送（手动催办与重复催办调度共用，R2-17） */
    @Autowired
    private com.privernal.flow.notice.FlowUrgeNotifier urgeNotifier;

    @Autowired
    private cn.dev33.satoken.dao.SaTokenDao saTokenDao;

    /** R2-18 票签并发锁：原子 setIfAbsent（RedisService），防双击记两票/并发双过 */
    @Autowired
    private com.privernal.common.redis.service.RedisService redisService;

    @Autowired
    private com.privernal.flow.mapper.FlowTimeoutTaskMapper flowTimeoutTaskMapper;

    private static final String RECONCILE_HOLDER = "daily-reconcile";

    @Override
    @DS("master")
    public Page<Task> todoPage(FlowTaskFilterDTO filter) {
        int pageNum = filter.getPageNum() == null ? 1 : filter.getPageNum();
        int pageSize = filter.getPageSize() == null ? 10 : filter.getPageSize();
        Page<FlowTask> page = new Page<>(pageNum, pageSize);
        List<String> flags = flowUserResolveService.expandCurrentUserFlags();
        return (Page<Task>) (Page) flowQueryMapper.selectTodoPage(page, flags,
                filter.getFlowName(), filter.getBusinessId(), filter.getNodeName(),
                filter.getTimeStart(), filter.getTimeEnd());
    }

    @Override
    @DS("master")
    public Page<HisTask> donePage(FlowTaskFilterDTO filter) {
        int pageNum = filter.getPageNum() == null ? 1 : filter.getPageNum();
        int pageSize = filter.getPageSize() == null ? 10 : filter.getPageSize();
        Page<FlowHisTask> page = new Page<>(pageNum, pageSize);
        return (Page<HisTask>) (Page) flowQueryMapper.selectDonePage(page, SecurityUtils.getUserId(),
                filter.getFlowName(), filter.getBusinessId(),
                filter.getTimeStart(), filter.getTimeEnd());
    }

    @Override
    @DS("master")
    public Map<String, Long> counts() {
        Map<String, Long> counts = new HashMap<>();
        List<String> flags = flowUserResolveService.expandCurrentUserFlags();
        counts.put("todo", flowQueryMapper.countTodo(flags));
        counts.put("done", flowQueryMapper.countDone(SecurityUtils.getUserId()));
        counts.put("start", flowQueryMapper.countMyInstance(SecurityUtils.getUserId()));
        return counts;
    }

    @Override
    @DS("master")
    public Instance approve(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        FlowContextHolder.FlowContext context = buildContext(handleDTO.getTaskId(), handleDTO.getVariable());
        FlowContextHolder.set(context);
        try {
            FlowParams params = buildParams(handleDTO)
                    .skipType(SkipType.PASS.getKey())
                    .variable(enrichGatewayVars(handleDTO.getVariable(), context));
            Instance instance = skipWithCountVote(handleDTO.getTaskId(), params);
            flowInstanceService.syncBusinessStatus(instance.getId());
            // 抄送节点自动通过，流程继续流转
            ccTaskHandler.autoSkipCcNodes(instance.getId());
            noHandlerTaskHandler.handleUnresolvedNodes(instance.getId());
            // 自动跳过（抄送/无人处理）可能把流程推进到办结，补一次状态回写防止单据停在「审批中」
            flowInstanceService.syncBusinessStatus(instance.getId());
            // 流转后重签本实例超时任务（§10.2 调用点）：下一节点可能配置了超时，
            // 此前仅转办/委派/加签/减签等动作触发注册，正常通过后的节点无人注册
            syncTimeoutAfter(handleDTO.getTaskId(), true);
            Instance latest = FlowEngine.insService().getById(instance.getId());
            flowEventPublisher.publishApprove(latest, latest.getNodeCode());
            if (FLOW_STATUS_PASS.equals(latest.getFlowStatus()) || FLOW_STATUS_FINISHED.equals(latest.getFlowStatus())) {
                flowEventPublisher.publishComplete(latest);
            }
            // R2-18 子流程桥接：本实例若停到子流程节点则发起子流程；本实例若为子流程且已结束则回推父流程
            flowInstanceService.launchSubflowTasks(latest.getId());
            flowInstanceService.resumeSubflowParent(latest.getId());
            return instance;
        } finally {
            FlowContextHolder.clear();
        }
    }

    /**
     * 手动催办：给任务当前办理人发催办消息（站内信落库 + 实时推送）。
     * 发起人或审批权限持有者可催办；同任务同催办人 10 分钟限一次，防连点轰炸。
     * 消息组装与发送抽到 FlowUrgeNotifier（R2-17 重复催办调度共用同一发送链）。
     */
    @Override
    @DS("master")
    public Map<String, Object> urge(Long taskId, String comment) {
        Task task = FlowEngine.taskService().getById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在或已办结");
        }
        // 办理人取任务在办用户（审批/转办/委派），与待办列表的口径一致
        List<String> handlers = urgeNotifier.resolveHandlers(taskId);
        if (handlers.isEmpty()) {
            throw new ServiceException("该任务当前没有办理人，无法催办");
        }
        String operator = SecurityUtils.getUserId();
        String lockKey = "FLOW_URGE_" + taskId + "_" + operator;
        if ("1".equals(saTokenDao.get(lockKey))) {
            throw new ServiceException("该任务您刚催办过，请 10 分钟后再试");
        }
        saTokenDao.set(lockKey, "1", 10 * 60);
        int sent = urgeNotifier.send(task, comment);
        Map<String, Object> result = new HashMap<>();
        result.put("urgedUsers", sent);
        return result;
    }

    /**
     * 离职移交（R2-17）：把移交人名下全部待办逐条转办给接收人（单条失败不影响其余）。
     * 转办走引擎原生 transfer：reductionHandlers 同时带裸 id 与 user: 前缀两种形态
     * （flow_user.processed_by 存在转换前后两种存储），只移除移交人的在办行、不动会签同事；
     * 逐实例触发超时/催办同步器（换人重置计时，办理人变更后的调度任务对齐）。
     */
    @Override
    @DS("master")
    public Map<String, Object> handover(String fromUserId, String toUserId, String message) {
        if (StringUtils.isEmpty(fromUserId) || StringUtils.isEmpty(toUserId)) {
            throw new ServiceException("移交人与接收人不能为空");
        }
        if (fromUserId.equals(toUserId)) {
            throw new ServiceException("接收人不能与移交人相同");
        }
        CommonResult<SystemUserDTO> target = remoteUserService.findUserById(toUserId, "");
        if (target == null || target.getCode() != CommonResult.SUCCESS || target.getData() == null) {
            throw new ServiceException("接收人不存在：" + toUserId);
        }
        List<String> flags = List.of(fromUserId,
                FlowPermissionTypeEnum.USER.getCode() + FlowConstant.PERMISSION_TYPE_SEPARATOR + fromUserId);
        List<FlowTask> tasks = flowQueryMapper.selectPendingTasksByHandlerFlags(flags);
        int success = 0;
        int failed = 0;
        Set<Long> instanceIds = new LinkedHashSet<>();
        Set<Long> resetTaskIds = new LinkedHashSet<>();
        for (Task task : tasks) {
            try {
                FlowParams params = FlowParams.build()
                        .handler(fromUserId)
                        .addHandlers(List.of(toUserId))
                        .reductionHandlers(List.of(fromUserId,
                                FlowPermissionTypeEnum.USER.getCode() + FlowConstant.PERMISSION_TYPE_SEPARATOR + fromUserId))
                        // 管理员代为移交：操作人不一定在办理人之列，绕过引擎的操作人校验（同超时处置口径）
                        .ignore(true)
                        .message(StringUtils.isEmpty(message) ? "离职移交" : message);
                if (FlowEngine.taskService().transfer(task.getId(), params)) {
                    success++;
                } else {
                    failed++;
                }
                instanceIds.add(task.getInstanceId());
                resetTaskIds.add(task.getId());
            } catch (Exception e) {
                failed++;
                log.warn("离职移交单条失败 taskId={} from={} to={}", task.getId(), fromUserId, toUserId, e);
            }
        }
        for (Long instanceId : instanceIds) {
            timeoutTaskSyncer.syncByInstanceAsync(instanceId, resetTaskIds);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", tasks.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("instanceCount", instanceIds.size());
        return result;
    }

    /**
     * 数量票签办理互斥锁（code-audit L1）：读票数→判定→记票/跳转三步非原子，
     * 两人同时通过可能各记一票都不流转（节点卡死），同一人双击会记两票。
     * 锁为任务粒度、原子 setIfAbsent（get+set 非原子挡不住并发窗口），拿不到锁直接拒绝；
     * 成功/异常路径都释放，锁 30s 自动过期兜底（进程崩溃不至于永久卡办理）。
     */
    private void lockVoteTask(Long taskId) {
        String lockKey = "FLOW_VOTE_" + taskId;
        boolean locked = redisService.setCacheObjectIfAbsent(lockKey, "1", 30L, java.util.concurrent.TimeUnit.SECONDS);
        if (!locked) {
            throw new ServiceException("该任务正在办理中，请稍后再试");
        }
    }

    private void unlockVoteTask(Long taskId) {
        redisService.deleteObject("FLOW_VOTE_" + taskId);
    }

    /**
     * 带绝对数量票签的通过跳转
     * <p>
     * 设计器「票签-固定通过人数」保存为 ext.voteType=count + ext.voteCount=N（warm-flow 引擎
     * 的 nodeRatio 仅支持百分比）。未达票数时记录一票并保留任务等待其他人；达到票数、仅剩
     * 最后一位办理人或非数量票签节点时按引擎原生 skip 完成（节点 nodeRatio=0 按或签完成整个节点）。
     * </p>
     *
     * @return 流程实例（记录一票时任务仍停留在当前节点）
     */
    private Instance skipWithCountVote(Long taskId, FlowParams params) {
        Task task = FlowEngine.taskService().getById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在或已办理");
        }
        Node node = ccTaskHandler.findNode(task);
        Map<String, String> ext = FlowNodeExtUtil.parseExt(node == null ? null : node.getExt());
        if (!"count".equals(ext.get(FlowNodeExtUtil.KEY_VOTE_TYPE))) {
            return FlowEngine.taskService().skip(taskId, params);
        }
        int need = FlowNodeExtUtil.parseIntValue(ext, FlowNodeExtUtil.KEY_VOTE_COUNT);
        List<User> todoList = FlowEngine.userService().listByAssociatedAndTypes(taskId,
                UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        if (need <= 0 || todoList == null || todoList.size() <= 1) {
            // 未配置票数或仅剩最后一位办理人：由当前人决定走向
            return FlowEngine.taskService().skip(taskId, params);
        }
        lockVoteTask(taskId);
        try {
            // 锁内判重：节点已被并发请求办结则拒绝再次办理。任务完成后 flow_task 行不删（引擎行为），
            // 不能按任务行判存在性；完整通过的历史记录 cooperate_type=审批（记票是 cooperate_type=票签），
            // 已有审批记录再 skip 会产生重复 PASS（audit L1 同族双过）
            if (hasApprovalRecord(taskId)) {
                throw new ServiceException("该任务已被其他办理人办结，请刷新待办");
            }
            List<HisTask> doneList = FlowEngine.hisTaskService()
                    .listByTaskIdAndCooperateTypes(taskId, CooperateType.VOTE.getKey());
            long passVotes = doneList == null ? 0 : doneList.stream()
                    .filter(his -> Objects.equals(his.getSkipType(), SkipType.PASS.getKey())).count();
            if (passVotes + 1 >= need) {
                // 达到通过票数，本次办理完成整个节点并跳转到下一节点
                return FlowEngine.taskService().skip(taskId, params);
            }
            // 未达票数：记录一票，移除当前办理人，任务继续等待投票
            User todoUser = lockedTodoList(taskId).stream()
                    .filter(user -> Objects.equals(user.getProcessedBy(), params.getHandler()))
                    .findFirst().orElseThrow(() -> new ServiceException("当前用户无该任务办理权限"));
            HisTask hisTask = FlowEngine.hisTaskService().setSignHisTask(task, params, "0", true);
            FlowEngine.hisTaskService().save(hisTask);
            FlowEngine.userService().removeById(todoUser.getId());
            syncTimeoutAfter(taskId);
            return FlowEngine.insService().getById(task.getInstanceId());
        } finally {
            unlockVoteTask(taskId);
        }
    }

    /**
     * 该任务是否已有「审批」类型的完整办理记录（区别于票签记票的「票签」类型）：
     * 有即节点已被办结，任何后续办理都应拒绝
     */
    private boolean hasApprovalRecord(Long taskId) {
        List<HisTask> approvalDone = FlowEngine.hisTaskService()
                .listByTaskIdAndCooperateTypes(taskId, CooperateType.APPROVAL.getKey());
        return approvalDone != null && !approvalDone.isEmpty();
    }

    /** 锁内重读任务当前办理人（并发下另一请求可能已被移除办理人） */
    private List<User> lockedTodoList(Long taskId) {
        List<User> todoList = FlowEngine.userService().listByAssociatedAndTypes(taskId,
                UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        return todoList == null ? new ArrayList<>() : todoList;
    }

    @Override
    @DS("master")
    public Instance reject(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        Task task = FlowEngine.taskService().getById(handleDTO.getTaskId());
        if (task == null) {
            throw new ServiceException("任务不存在或已办理");
        }
        FlowRejectTypeEnum rejectType = FlowRejectTypeEnum.ofCode(handleDTO.getRejectType());
        FlowContextHolder.FlowContext context = buildContext(handleDTO.getTaskId(), handleDTO.getVariable());
        FlowContextHolder.set(context);
        try {
            // 绝对数量票签节点优先走「固定驳回人数」计数分支
            Instance instance = rejectWithCountVote(handleDTO, task, rejectType, context);
            if (instance == null) {
                switch (rejectType) {
                    case LAST:
                        instance = FlowEngine.taskService().rejectLast(handleDTO.getTaskId(), buildParams(handleDTO));
                        break;
                    case START:
                        String startNodeCode = findStartNodeCode(task.getDefinitionId());
                        if (StringUtils.isEmpty(startNodeCode)) {
                            throw new ServiceException("未找到发起节点");
                        }
                        // warm-flow 禁止把跳转落点放在开始节点（START_NODE_NOT_ALLOW_JUMP），
                        // 「驳回到发起人」适配为驳回到发起后的第一个办理节点；
                        // 落点是抄送节点时由 autoSkipCcNodes 自动流过
                        String firstBetweenCode = findFirstBetweenNodeCode(task.getDefinitionId(), startNodeCode);
                        if (StringUtils.isEmpty(firstBetweenCode)) {
                            throw new ServiceException("未找到发起后的首个办理节点");
                        }
                        instance = FlowEngine.taskService().skip(handleDTO.getTaskId(),
                                buildParams(handleDTO).skipType(SkipType.REJECT.getKey())
                                        .nodeCode(firstBetweenCode).variable(enrichGatewayVars(handleDTO.getVariable(), context)));
                        break;
                    case TARGET:
                    default:
                        if (StringUtils.isEmpty(handleDTO.getTargetNodeCode())) {
                            throw new ServiceException("目标节点编码不能为空");
                        }
                        instance = FlowEngine.taskService().skip(handleDTO.getTaskId(),
                                buildParams(handleDTO).skipType(SkipType.REJECT.getKey())
                                        .nodeCode(handleDTO.getTargetNodeCode()).variable(enrichGatewayVars(handleDTO.getVariable(), context)));
                        break;
                }
            }
            flowInstanceService.syncBusinessStatus(instance.getId());
            // 驳回落点若为抄送节点同样自动通过
            ccTaskHandler.autoSkipCcNodes(instance.getId());
            noHandlerTaskHandler.handleUnresolvedNodes(instance.getId());
            // 自动跳过可能推进流程结束，补一次状态回写（同 approve）
            flowInstanceService.syncBusinessStatus(instance.getId());
            // 驳回后重签本实例超时任务（§10.2 调用点）
            syncTimeoutAfter(handleDTO.getTaskId(), true);
            flowEventPublisher.publishReject(instance, instance.getNodeCode());
            // R2-18 子流程桥接：驳回落点也可能是子流程节点（发起失败不阻断驳回主链路）
            flowInstanceService.launchSubflowTasks(instance.getId());
            return instance;
        } finally {
            FlowContextHolder.clear();
        }
    }

    /**
     * 带绝对数量票签的驳回跳转
     * <p>
     * 设计器「票签-固定驳回人数」保存为 ext.voteType=count + ext.voteRejectCount=N（配合节点
     * anyNodeSkip 指定驳回落点）。未达驳回票数时记录一票并保留任务等待其他人；达到票数时整个
     * 节点驳回到 anyNodeSkip 指定节点；未配置票数、仅剩最后一位办理人或未配置落点时返回 null，
     * 交回原生驳回路由。
     * </p>
     *
     * @return null 表示非数量票签或应退回原生驳回路由；否则为记票后/驳回后的流程实例
     */
    private Instance rejectWithCountVote(FlowTaskHandleDTO handleDTO, Task task,
                                         FlowRejectTypeEnum rejectType, FlowContextHolder.FlowContext context) {
        Long taskId = task.getId();
        Node node = ccTaskHandler.findNode(task);
        Map<String, String> ext = FlowNodeExtUtil.parseExt(node == null ? null : node.getExt());
        if (!"count".equals(ext.get(FlowNodeExtUtil.KEY_VOTE_TYPE))) {
            return null;
        }
        int need = FlowNodeExtUtil.parseIntValue(ext, FlowNodeExtUtil.KEY_VOTE_REJECT_COUNT);
        List<User> todoList = FlowEngine.userService().listByAssociatedAndTypes(taskId,
                UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        if (need <= 0 || todoList == null || todoList.size() <= 1) {
            // 未配置驳回票数或仅剩最后一位办理人：由当前人决定走向
            return null;
        }
        lockVoteTask(taskId);
        try {
            // 锁内判重（同 skipWithCountVote）：已有审批记录即节点已走完
            if (hasApprovalRecord(taskId)) {
                throw new ServiceException("该任务已被其他办理人办结，请刷新待办");
            }
            List<HisTask> doneList = FlowEngine.hisTaskService()
                    .listByTaskIdAndCooperateTypes(taskId, CooperateType.VOTE.getKey());
            long rejectVotes = doneList == null ? 0 : doneList.stream()
                    .filter(his -> Objects.equals(his.getSkipType(), SkipType.REJECT.getKey())).count();
            if (rejectVotes + 1 >= need) {
                String anyNodeSkip = node == null ? null : node.getAnyNodeSkip();
                if (StringUtils.isEmpty(anyNodeSkip)) {
                    // 未配置驳回落点：退回原生路由（保持 rejectType 语义）
                    return null;
                }
                return FlowEngine.taskService().skip(taskId,
                        buildParams(handleDTO).skipType(SkipType.REJECT.getKey())
                                .nodeCode(anyNodeSkip).variable(enrichGatewayVars(handleDTO.getVariable(), context)));
            }
            // 未达驳回票数：记录一票驳回，移除当前办理人，任务继续等待投票
            FlowParams params = buildParams(handleDTO).skipType(SkipType.REJECT.getKey());
            User todoUser = lockedTodoList(taskId).stream()
                    .filter(user -> Objects.equals(user.getProcessedBy(), params.getHandler()))
                    .findFirst().orElseThrow(() -> new ServiceException("当前用户无该任务办理权限"));
            HisTask hisTask = FlowEngine.hisTaskService().setSignHisTask(task, params, "0", false);
            FlowEngine.hisTaskService().save(hisTask);
            FlowEngine.userService().removeById(todoUser.getId());
            syncTimeoutAfter(taskId);
            return FlowEngine.insService().getById(task.getInstanceId());
        } finally {
            unlockVoteTask(taskId);
        }
    }

    @Override
    @DS("master")
    public boolean transfer(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        boolean result = FlowEngine.taskService().transfer(handleDTO.getTaskId(), buildCooperateParams(handleDTO));
        syncTimeoutAfter(handleDTO.getTaskId(), true);
        return result;
    }

    @Override
    @DS("master")
    public boolean depute(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        boolean result = FlowEngine.taskService().depute(handleDTO.getTaskId(), buildCooperateParams(handleDTO));
        syncTimeoutAfter(handleDTO.getTaskId(), true);
        return result;
    }

    @Override
    @DS("master")
    public boolean addSignature(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        boolean result = FlowEngine.taskService().addSignature(handleDTO.getTaskId(), buildCooperateParams(handleDTO));
        syncTimeoutAfter(handleDTO.getTaskId(), true);
        return result;
    }

    @Override
    @DS("master")
    public boolean reductionSignature(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        if (handleDTO.getUserIds() == null || handleDTO.getUserIds().isEmpty()) {
            throw new ServiceException("减签人员不能为空");
        }
        FlowParams params = FlowParams.build()
                .handler(resolveHandler(handleDTO.getHandler()))
                .reductionHandlers(handleDTO.getUserIds())
                .message(handleDTO.getMessage());
        boolean result = FlowEngine.taskService().reductionSignature(handleDTO.getTaskId(), params);
        syncTimeoutAfter(handleDTO.getTaskId(), true);
        return result;
    }

    @Override
    @DS("master")
    public Instance jump(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        if (StringUtils.isEmpty(handleDTO.getTargetNodeCode())) {
            throw new ServiceException("目标节点编码不能为空");
        }
        String skipType = StringUtils.isEmpty(handleDTO.getSkipType())
                ? SkipType.PASS.getKey() : handleDTO.getSkipType();
        FlowParams params = buildParams(handleDTO)
                .skipType(skipType)
                .nodeCode(handleDTO.getTargetNodeCode());
        Instance instance = FlowEngine.taskService().skip(handleDTO.getTaskId(), params);
        flowInstanceService.syncBusinessStatus(instance.getId());
        ccTaskHandler.autoSkipCcNodes(instance.getId());
        noHandlerTaskHandler.handleUnresolvedNodes(instance.getId());
        // 自动跳过可能推进流程结束，补一次状态回写（同 approve）
        flowInstanceService.syncBusinessStatus(instance.getId());
        return instance;
    }

    @Override
    @DS("master")
    public Instance pending(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        Instance instance = FlowEngine.taskService().pending(handleDTO.getTaskId(), buildParams(handleDTO));
        syncTimeoutAfter(handleDTO.getTaskId());
        return instance;
    }

    @Override
    @DS("master")
    public Instance retrieve(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        Instance instance = FlowEngine.taskService().taskBack(handleDTO.getTaskId(), buildParams(handleDTO));
        flowInstanceService.syncBusinessStatus(instance.getId());
        syncTimeoutAfter(handleDTO.getTaskId());
        return instance;
    }

    @Override
    @DS("master")
    public List<HisTask> records(Long instanceId) {
        return FlowEngine.hisTaskService().getByInsId(instanceId);
    }

    @Override
    @DS("master")
    public FlowTaskOptionDTO taskOptions(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务id不能为空");
        }
        Task task = FlowEngine.taskService().getById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在或已办理");
        }
        List<User> taskUsers = FlowEngine.userService().listByAssociatedAndTypes(taskId,
                UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
        List<String> storedFlags = new ArrayList<>();
        if (taskUsers != null) {
            for (User user : taskUsers) {
                if (user != null && StringUtils.isNotEmpty(user.getProcessedBy())) {
                    storedFlags.add(user.getProcessedBy());
                }
            }
        }
        // 权限：仅任务办理人可查看办理选项（与待办列表可见范围一致）
        List<String> currentFlags = flowUserResolveService.expandCurrentUserFlags();
        if (storedFlags.stream().noneMatch(currentFlags::contains)) {
            throw new ServiceException("当前用户无该任务办理权限");
        }
        FlowTaskOptionDTO optionDTO = new FlowTaskOptionDTO();
        // 节点选项：复用实例节点查询（含 PENDING/ACTIVE/COMPLETED 状态）
        optionDTO.setNodeList(flowInstanceService.getInstanceNodes(task.getInstanceId()));
        optionDTO.setTaskUserList(buildTaskUserOptions(storedFlags));
        return optionDTO;
    }

    /**
     * 办理人选项：processed_by 为裸用户id或 user: 前缀时解析昵称展示，
     * 其余历史标识（role:/dept: 等）原样展示；userId 保持原值供减签提交
     */
    private List<FlowTaskOptionDTO.TaskUserOptionDTO> buildTaskUserOptions(List<String> storedFlags) {
        String userPrefix = FlowPermissionTypeEnum.USER.getCode() + FlowConstant.PERMISSION_TYPE_SEPARATOR;
        Set<String> pureUserIds = new LinkedHashSet<>();
        for (String flag : storedFlags) {
            if (flag.startsWith(userPrefix)) {
                pureUserIds.add(flag.substring(userPrefix.length()));
            } else if (!flag.contains(FlowConstant.PERMISSION_TYPE_SEPARATOR)) {
                pureUserIds.add(flag);
            }
        }
        Map<String, String> nameById = new HashMap<>();
        if (!pureUserIds.isEmpty()) {
            WorkflowUserFilterDTO filter = new WorkflowUserFilterDTO();
            filter.setUserList(new ArrayList<>(pureUserIds));
            CommonResult<List<SystemUserDTO>> result = remoteUserService.findUserListByIds(filter, "");
            if (result != null && result.getData() != null) {
                for (SystemUserDTO user : result.getData()) {
                    if (user != null && StringUtils.isNotEmpty(user.getId())) {
                        nameById.put(user.getId(),
                                StringUtils.isNotEmpty(user.getNickName()) ? user.getNickName() : user.getLoginName());
                    }
                }
            }
        }
        List<FlowTaskOptionDTO.TaskUserOptionDTO> userOptions = new ArrayList<>();
        for (String flag : storedFlags) {
            FlowTaskOptionDTO.TaskUserOptionDTO option = new FlowTaskOptionDTO.TaskUserOptionDTO();
            option.setUserId(flag);
            String pureUserId = flag.startsWith(userPrefix)
                    ? flag.substring(userPrefix.length())
                    : (flag.contains(FlowConstant.PERMISSION_TYPE_SEPARATOR) ? null : flag);
            option.setUserName(pureUserId != null ? nameById.getOrDefault(pureUserId, pureUserId) : flag);
            userOptions.add(option);
        }
        return userOptions;
    }

    private void checkTaskId(FlowTaskHandleDTO handleDTO) {
        if (handleDTO == null || handleDTO.getTaskId() == null) {
            throw new ServiceException("任务id不能为空");
        }
    }

    private FlowParams buildParams(FlowTaskHandleDTO handleDTO) {
        return FlowParams.build()
                .handler(resolveHandler(handleDTO.getHandler()))
                .message(handleDTO.getMessage());
    }

    /**
     * 委托/转办/加签参数（addHandlers 必传）
     */
    private FlowParams buildCooperateParams(FlowTaskHandleDTO handleDTO) {
        if (handleDTO.getUserIds() == null || handleDTO.getUserIds().isEmpty()) {
            throw new ServiceException("目标办理人不能为空");
        }
        return FlowParams.build()
                .handler(resolveHandler(handleDTO.getHandler()))
                .addHandlers(handleDTO.getUserIds())
                .message(handleDTO.getMessage());
    }

    /**
     * 办理人取值（S12）：有登录态一律以登录用户为准——办理人若采信请求体，
     * 任何人可代他人审批并伪造留痕；显式传入仅限无登录态的内部服务调用
     */
    private String resolveHandler(String explicitHandler) {
        String loginUserId = SecurityUtils.getUserId();
        if (StringUtils.isNotEmpty(loginUserId)) {
            return loginUserId;
        }
        return StringUtils.isEmpty(explicitHandler) ? "" : explicitHandler;
    }

    /**
     * 查找流程定义的发起节点编码
     */
    private String findStartNodeCode(Long definitionId) {
        Node condition = FlowEngine.newNode();
        condition.setDefinitionId(definitionId);
        condition.setNodeType(NodeType.START.getKey());
        List<Node> nodes = FlowEngine.nodeService().list(condition);
        if (nodes != null && !nodes.isEmpty()) {
            return nodes.get(0).getNodeCode();
        }
        return null;
    }

    /**
     * 从开始节点沿跳转线广度优先找第一个中间节点（含抄送，抄送由 autoSkipCcNodes 自动流过）。
     * 用于「驳回到发起人」：引擎禁止把跳转落点放在开始节点。
     */
    private String findFirstBetweenNodeCode(Long definitionId, String startNodeCode) {
        List<Skip> skips = FlowEngine.skipService().list(
                FlowEngine.newSkip().setDefinitionId(definitionId));
        List<Node> nodes = FlowEngine.nodeService().list(
                FlowEngine.newNode().setDefinitionId(definitionId));
        if (skips == null || nodes == null || nodes.isEmpty()) {
            return null;
        }
        Map<String, String> nodeTypes = new HashMap<>();
        for (Node node : nodes) {
            nodeTypes.put(node.getNodeCode(), String.valueOf(node.getNodeType()));
        }
        Set<String> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(startNodeCode);
        visited.add(startNodeCode);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Skip skip : skips) {
                if (!current.equals(skip.getNowNodeCode())) {
                    continue;
                }
                String next = skip.getNextNodeCode();
                if (next == null || !visited.add(next)) {
                    continue;
                }
                if ("1".equals(nodeTypes.get(next))) {
                    return next;
                }
                queue.add(next);
            }
        }
        return null;
    }

    /**
     * 办理链路上下文：发起人与组织必须从实例反查，表单变量要合并实例上已有的，
     * 否则第二个节点之后的 script:INITIATOR / FORM_FIELD_USER / DATA_OWNER 会取到「上一个审批人」和空值。
     */
    private FlowContextHolder.FlowContext buildContext(Long taskId, Map<String, Object> variable) {
        FlowContextHolder.FlowContext context = new FlowContextHolder.FlowContext();
        context.setInitiatorId(SecurityUtils.getUserId());
        context.setInitiatorName(SecurityUtils.getNickName());
        context.setOrganizationId(SecurityUtils.getOrganizationId());
        context.setDepartmentId(SecurityUtils.getDepartmentId());
        Map<String, Object> variables = new HashMap<>();
        try {
            Task task = FlowEngine.taskService().getById(taskId);
            Instance instance = task == null ? null : FlowEngine.insService().getById(task.getInstanceId());
            if (instance != null) {
                if (StringUtils.isNotEmpty(instance.getCreateBy())) {
                    context.setInitiatorId(instance.getCreateBy());
                    var userResult = remoteUserService.findUserById(instance.getCreateBy(), "");
                    if (userResult != null && userResult.getData() != null) {
                        if (StringUtils.isNotEmpty(userResult.getData().getOrganizationId())) {
                            context.setOrganizationId(userResult.getData().getOrganizationId());
                        }
                        if (StringUtils.isNotEmpty(userResult.getData().getDepartmentId())) {
                            context.setDepartmentId(userResult.getData().getDepartmentId());
                        }
                        if (StringUtils.isNotEmpty(userResult.getData().getNickName())) {
                            context.setInitiatorName(userResult.getData().getNickName());
                        }
                    }
                }
                if (instance.getVariableMap() != null) {
                    variables.putAll(instance.getVariableMap());
                }
            }
        } catch (Exception e) {
            log.warn("构建办理上下文失败，退回当前登录用户与请求变量 taskId={}", taskId, e);
        }
        if (variable != null) {
            variables.putAll(variable);
        }
        context.setVariables(variables);
        return context;
    }

    /**
     * 网关上下文变量：把发起人/部门/组织/角色名以保留键并入审批请求携带的流程变量，
     * 供网关流转条件（wfexpr）的「组织机构」「角色」分组引用（发起时的变量由
     * CommonController#flowVariables 注入同一套键；此处覆盖审批链路中前端随办理提交变量的场景）。
     */
    private Map<String, Object> enrichGatewayVars(Map<String, Object> variable, FlowContextHolder.FlowContext context) {
        if (variable == null) {
            return null;
        }
        Map<String, Object> merged = new HashMap<>(variable);
        if (context == null) {
            return merged;
        }
        if (StringUtils.isNotEmpty(context.getInitiatorId())) {
            merged.putIfAbsent(FlowExprConditionStrategy.INITIATOR, context.getInitiatorId());
            merged.putIfAbsent(FlowExprConditionStrategy.INITIATOR_ORG, context.getOrganizationId());
            merged.putIfAbsent(FlowExprConditionStrategy.INITIATOR_DEPT, context.getDepartmentId());
            if (StringUtils.isNotEmpty(context.getInitiatorName())) {
                merged.putIfAbsent(FlowExprConditionStrategy.INITIATOR_NAME, context.getInitiatorName());
            }
            try {
                List<String> roleNames = flowRoleNameMapper.selectRoleNamesByUserId(context.getInitiatorId());
                if (roleNames != null && !roleNames.isEmpty()) {
                    merged.putIfAbsent(FlowExprConditionStrategy.INITIATOR_ROLES, roleNames);
                }
            } catch (Exception e) {
                log.warn("查询发起人角色失败，HAS_ROLE 条件按无角色处理 initiator={}", context.getInitiatorId(), e);
            }
        }
        return merged;
    }

    @Override
    @DS("master")
    public Instance claim(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        String userId = SecurityUtils.getUserId();
        Task task = FlowEngine.taskService().getById(handleDTO.getTaskId());
        if (task == null) {
            throw new ServiceException("任务不存在或已办理");
        }
        FlowParams params = FlowParams.build()
                .handler(userId)
                .addHandlers(List.of(userId))
                .message(StringUtils.isEmpty(handleDTO.getMessage()) ? "签收任务" : handleDTO.getMessage());
        FlowEngine.taskService().transfer(handleDTO.getTaskId(), params);
        Instance instance = FlowEngine.insService().getById(task.getInstanceId());
        if (instance != null) {
            flowInstanceService.syncBusinessStatus(instance.getId());
        }
        syncTimeoutAfter(handleDTO.getTaskId(), true);
        return instance;
    }

    @Override
    @DS("master")
    public Instance changePerson(FlowTaskHandleDTO handleDTO) {
        checkTaskId(handleDTO);
        if (handleDTO.getUserIds() == null || handleDTO.getUserIds().isEmpty()) {
            throw new ServiceException("目标审批人不能为空");
        }
        Task task = FlowEngine.taskService().getById(handleDTO.getTaskId());
        if (task == null) {
            throw new ServiceException("任务不存在或已办理");
        }
        FlowParams params = FlowParams.build()
                .handler(SecurityUtils.getUserId())
                .addHandlers(handleDTO.getUserIds())
                .message(StringUtils.isEmpty(handleDTO.getMessage()) ? "更换审批人" : handleDTO.getMessage());
        FlowEngine.taskService().transfer(handleDTO.getTaskId(), params);
        Instance instance = FlowEngine.insService().getById(task.getInstanceId());
        if (instance != null) {
            flowInstanceService.syncBusinessStatus(instance.getId());
        }
        syncTimeoutAfter(handleDTO.getTaskId(), true);
        return instance;
    }

    @Override
    @DS("master")
    public Instance adjust(FlowAdjustTaskDTO adjustDTO) {
        if (adjustDTO == null || adjustDTO.getTaskId() == null) {
            throw new ServiceException("任务id不能为空");
        }
        Task task = FlowEngine.taskService().getById(adjustDTO.getTaskId());
        if (task == null) {
            throw new ServiceException("任务不存在或已办理");
        }
        String adjustType = adjustDTO.getAdjustType();
        if ("CHANGE_ASSIGNEE".equals(adjustType)) {
            if (adjustDTO.getUserIds() == null || adjustDTO.getUserIds().isEmpty()) {
                throw new ServiceException("目标审批人不能为空");
            }
            FlowParams params = FlowParams.build()
                    .handler(resolveHandler(adjustDTO.getHandler()))
                    .addHandlers(adjustDTO.getUserIds())
                    .message(StringUtils.isEmpty(adjustDTO.getMessage()) ? "管理员更换审批人" : adjustDTO.getMessage());
            FlowEngine.taskService().transfer(adjustDTO.getTaskId(), params);
            Instance instance = FlowEngine.insService().getById(task.getInstanceId());
            if (instance != null) {
                flowInstanceService.syncBusinessStatus(instance.getId());
            }
            syncTimeoutAfter(adjustDTO.getTaskId(), true);
            return instance;
        } else if ("CHANGE_NODE".equals(adjustType)) {
            if (StringUtils.isEmpty(adjustDTO.getTargetNodeCode())) {
                throw new ServiceException("目标节点编码不能为空");
            }
            FlowParams params = FlowParams.build()
                    .handler(resolveHandler(adjustDTO.getHandler()))
                    .message(StringUtils.isEmpty(adjustDTO.getMessage()) ? "管理员调整节点" : adjustDTO.getMessage())
                    .skipType(SkipType.PASS.getKey())
                    .nodeCode(adjustDTO.getTargetNodeCode());
            Instance instance = FlowEngine.taskService().skip(adjustDTO.getTaskId(), params);
            flowInstanceService.syncBusinessStatus(instance.getId());
            syncTimeoutAfter(adjustDTO.getTaskId());
            return instance;
        } else {
            throw new ServiceException("不支持的调整类型：" + adjustType);
        }
    }

    /**
 * 超时兜底扫描：迁移到任务表后降级为每日一次——主链路是
     * FlowTimeoutTaskSyncer 随流转注册的一次性任务；本方法只兜两类：
     * ① syncer 漏注册/注册失败的"有配置"待办（按任务行存在性判定，不再被配置无条件排除）；
     * ② 未配置超时节点沿用默认 48h 自动通过（历史行为）。持 DB 租约锁修复多实例重复处理。
     */
    @Override
    @DS("master")
    @Scheduled(cron = "0 30 3 * * ?")
    public void handleTimeoutTasks() {
        if (flowTimeoutTaskMapper.tryReconcileLock(RECONCILE_HOLDER, 3600) == 0) {
            log.info("超时兜底锁被他实例持有，本实例跳过");
            return;
        }
        try {
            doHandleTimeoutTasks();
        } finally {
            flowTimeoutTaskMapper.releaseReconcileLock(RECONCILE_HOLDER);
        }
    }

    private void doHandleTimeoutTasks() {
        log.info("开始扫描超时任务（每日兜底）...");
        int autoApproveCount = 0;
        int autoRejectCount = 0;
        int notifyCount = 0;
        int orphanCount = 0;
        // 1. 设计器「超时设置」配置的节点（ext.wfTimeoutHours/wfTimeoutAction）
        java.util.Set<Long> configuredTaskIds = new java.util.HashSet<>();
        try {
            List<FlowTodoTaskExtDTO> todoTasks = flowQueryMapper.selectTodoTasksWithNodeExt();
            if (todoTasks != null) {
                for (FlowTodoTaskExtDTO item : todoTasks) {
                    Map<String, String> ext = FlowNodeExtUtil.parseExt(item.getNodeExt());
                    if (FlowNodeExtUtil.resolveTimeoutHours(ext) <= 0) {
                        continue;
                    }
                    configuredTaskIds.add(item.getTaskId());
                    if (!isOverdue(item.getCreateTime(), FlowNodeExtUtil.resolveTimeoutHours(ext))) {
                        continue;
                    }
                    String action = ext.getOrDefault(FlowNodeExtUtil.KEY_TIMEOUT_ACTION, FlowNodeExtUtil.TIMEOUT_AUTO_PASS);
                    try {
                        Task task = FlowEngine.taskService().getById(item.getTaskId());
                        if (task == null) {
                            continue;
                        }
                        switch (action) {
                            case FlowNodeExtUtil.TIMEOUT_AUTO_REJECT -> {
                                timeoutAutoReject(task);
                                autoRejectCount++;
                            }
                            case FlowNodeExtUtil.TIMEOUT_NOTIFY -> {
                                // P0-8（§10.3 只提醒一次）：NOTIFY 不办结待办，天然每天逾期。
                                // 判定必须"存在过任务行"（含 DONE），只查未终态会在一次性提醒转 DONE 后
                                // 被每日兜底重新注册 → 每天重发一次提醒。
                                String notifyBizKey = com.privernal.flow.handler.FlowTimeoutTaskSyncer.bizKey(
                                        findInstanceIdByTaskId(task.getId()), task.getId());
                                if (flowTimeoutTaskMapper.existsAnyTask(notifyBizKey) > 0) {
                                    log.debug("NOTIFY 已提醒过（任务行存在），跳过重复提醒 bizKey={}", notifyBizKey);
                                    break;
                                }
                                publishTimeoutEvent(task, "任务超时提醒：请及时办理");
                                notifyCount++;
                            }
                            default -> {
                                timeoutAutoPass(task);
                                autoApproveCount++;
                            }
                        }
                    } catch (Exception e) {
                        log.error("超时任务处理失败 taskId={}", item.getTaskId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("超时任务扫描异常（节点配置）", e);
        }
        // 2. 兜底扫描只接"配置了超时但漏注册"的待办；未配置超时的节点不再默认 48h 自动通过——
        //    用户无感知的"超时即同意"是合规风险，待办保持挂起，由设计器提示与节点配置引导处置
        int unconfiguredCount = 0;
        try {
            List<FlowTask> timeoutTasks = flowQueryMapper.selectTimeoutTasks(DEFAULT_TIMEOUT_HOURS);
            if (timeoutTasks != null) {
                for (FlowTask flowTask : timeoutTasks) {
                    if (!configuredTaskIds.contains(flowTask.getId())) {
                        unconfiguredCount++;
                        continue;
                    }
                    // §10.5：迁移后"有配置"不再无条件排除兜底——syncer 漏注册/注册失败时
                    // 任务行不存在，必须继续走兜底，否则超时处置彻底消失且无告警
                    try {
                        String bizKey = com.privernal.flow.handler.FlowTimeoutTaskSyncer.bizKey(
                                findInstanceIdByTaskId(flowTask.getId()), flowTask.getId());
                        if (flowTimeoutTaskMapper.existsActiveTask(bizKey) > 0) {
                            continue;
                        }
                        orphanCount++;
                        log.error("超时兜底接住漏注册待办 taskId={} bizKey={}（配置了超时但任务表无行）",
                                flowTask.getId(), bizKey);
                        Task task = FlowEngine.taskService().getById(flowTask.getId());
                        if (task == null) {
                            continue;
                        }
                        timeoutAutoPass(task);
                        autoApproveCount++;
                    } catch (Exception e) {
                        log.error("超时任务处理失败 taskId={}", flowTask.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("超时任务扫描异常（默认超时）", e);
        }
        if (unconfiguredCount > 0) {
            log.info("{} 条待办超过 {} 小时且节点未配置超时处置：保持挂起，不自动通过（可在设计器节点超时设置里配置）",
                    unconfiguredCount, DEFAULT_TIMEOUT_HOURS);
        }
        log.info("超时兜底处理完成：自动通过 {} 条（含漏注册兜底 {}），自动驳回 {} 条，提醒 {} 条",
                autoApproveCount, orphanCount, autoRejectCount, notifyCount);
    }

    /**
     * 流转事件后异步重签本实例的超时任务（§10.2 调用点统一入口）；
     * 事务内只做一次待办查询，注册动作在 syncer 线程池完成。
     *
     * @param resetTiming true=办理人变更类操作（转办/委派/签收/换人/加签/减签）：
     *                    该待办计时从变更时刻重算（D3-B；warm-flow updateHandler
     *                    保留原任务行，create_time 不变，必须显式重置）
     */
    private void syncTimeoutAfter(Long taskId) {
        syncTimeoutAfter(taskId, false);
    }

    private void syncTimeoutAfter(Long taskId, boolean resetTiming) {
        try {
            Task task = FlowEngine.taskService().getById(taskId);
            if (task != null && task.getInstanceId() != null) {
                timeoutTaskSyncer.syncByInstanceAsync(task.getInstanceId(),
                        resetTiming ? java.util.Set.of(taskId) : null);
            }
        } catch (Exception e) {
            log.warn("流转后触发超时同步失败 taskId={}", taskId, e);
        }
    }

    /**
     * 超时自动通过/驳回/提醒统一委托 WorkflowTimeoutActionHandler：
     * 调度触发的处置与每日兜底共用同一段逻辑，避免双份实现漂移
     */
    private void timeoutAutoPass(Task task) {
        workflowTimeoutActionHandler.timeoutAutoPass(task);
    }

    private void timeoutAutoReject(Task task) {
        workflowTimeoutActionHandler.timeoutAutoReject(task);
    }

    /**
     * 发布超时事件（仅提醒场景）
     */
    private void publishTimeoutEvent(Task task, String message) {
        workflowTimeoutActionHandler.publishTimeoutNotify(task, message);
    }

    private void publishTimeoutEvent(Instance instance, String message) {
        flowEventPublisher.publishTimeout(instance.getId(), resolveFlowCode(instance),
                instance.getBusinessId(), message);
    }

    /** 兜底扫描用：按待办反查实例 id（查不到返回 null，此时跳过存在性判定按兜底处置） */
    private Long findInstanceIdByTaskId(Long taskId) {
        try {
            Task task = FlowEngine.taskService().getById(taskId);
            return task == null ? null : task.getInstanceId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 任务创建时间是否已超过指定小时数
     */
    private boolean isOverdue(Date createTime, int timeoutHours) {
        return createTime != null
                && System.currentTimeMillis() - createTime.getTime() >= timeoutHours * 3600_000L;
    }

    /**
     * 按实例定义解析流程编码
     */
    private String resolveFlowCode(Instance instance) {
        if (instance == null || instance.getDefinitionId() == null) {
            return null;
        }
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        return definition == null ? null : definition.getFlowCode();
    }
}
