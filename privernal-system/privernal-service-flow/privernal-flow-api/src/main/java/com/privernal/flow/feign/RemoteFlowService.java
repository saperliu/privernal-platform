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

package com.privernal.flow.feign;

import com.privernal.common.web.domain.CommonResult;
import com.privernal.flow.dto.FlowAdjustTaskDTO;
import com.privernal.flow.dto.FlowFormPermissionDTO;
import com.privernal.flow.dto.FlowInstanceStatusDTO;
import com.privernal.flow.dto.FlowStartDTO;
import com.privernal.flow.dto.FlowTaskHandleDTO;

/**
 * 工作流内部服务接口（单体本地 Bean 模式，与 RemoteDynamicService 一致）
 * 供 dynamic 等模块在动态表单数据提交时自动发起流程、查询流程状态
 *
 * @author saperliu
 */
public interface RemoteFlowService {

    /**
     * 启动流程
     *
     * @param startDTO 启动参数（flowCode/tableCode 二选一 + businessId + variables）
     * @return 流程实例状态
     */
    CommonResult<FlowInstanceStatusDTO> start(FlowStartDTO startDTO);

    /**
     * 按业务数据id查询流程状态
     *
     * @param businessId 业务数据id
     * @return 流程状态（无实例时 data 为 null，code 200）
     */
    CommonResult<FlowInstanceStatusDTO> getStatusByBusinessId(String businessId);

    /**
     * 当前登录人是否为该业务数据所在流程实例的参与人（发起人、曾任办理人/协办人、当前待办人）。
     * 动态表单数据接口用它给审批人放行只读：审批人通常没有申请单所在表的按钮权限。
     *
     * @param tableCode  调用方声称的业务表编码，需与实例解析出的表一致
     * @param businessId 业务数据id
     */
    CommonResult<Boolean> isBusinessDataParticipant(String tableCode, String businessId);

    /**
     * 按业务数据id查询运行时表单权限（当前节点字段/按钮权限 + 当前用户是否审批人）
     *
     * @param tableCode  动态表单编码
     * @param businessId 业务数据id
     * @return 表单权限
     */
    CommonResult<FlowFormPermissionDTO> getFormPermission(String tableCode, String businessId);

    /**
     * 审批通过（内部代办）
     *
     * @param handleDTO 任务操作参数（taskId 必传，无登录态时 handler 必传）
     * @return 操作后的实例状态
     */
    CommonResult<FlowInstanceStatusDTO> approve(FlowTaskHandleDTO handleDTO);

    /**
     * 驳回（内部代办，rejectType：LAST 上一节点 / START 发起节点 / TARGET 指定节点）
     */
    CommonResult<FlowInstanceStatusDTO> reject(FlowTaskHandleDTO handleDTO);

    /**
     * 撤回/拿回（发起人将已办理任务取回到待办，对应 wfs 的 undo）
     */
    CommonResult<FlowInstanceStatusDTO> retrieve(FlowTaskHandleDTO handleDTO);

    /**
     * 转办（将当前任务转交给其他人，对应 wfs 的 assignee）
     */
    CommonResult<Boolean> transfer(FlowTaskHandleDTO handleDTO);

    /**
     * 终止流程实例（对应 wfs 的 terminate；无登录态时 handler 必传）
     */
    CommonResult<FlowInstanceStatusDTO> terminate(Long instanceId, String message, String handler);

    /**
     * 重启流程实例（被终止/作废的流程重新启动，对应 wfs 的 restart）
     */
    CommonResult<FlowInstanceStatusDTO> restart(Long instanceId, String message);

    /**
     * 管理员调整任务（更换审批人或跳转指定节点，对应 wfs 的 adjust）
     */
    CommonResult<FlowInstanceStatusDTO> adjust(FlowAdjustTaskDTO adjustDTO);

    /**
     * 按实例id查询流程状态
     *
     * @param instanceId 流程实例id
     * @return 流程状态（无实例时 data 为 null，code 200）
     */
    CommonResult<FlowInstanceStatusDTO> getStatusByInstanceId(Long instanceId);

    /**
     * 按流程编码删除全部版本的流程（联动清理运行实例、待办/已办任务、表单绑定与节点配置）。
     * 动态表单删除时调用。
     *
     * @param flowCode 流程编码（动态表单的 workflowCode）
     * @return 删除的定义数量
     */
    CommonResult<Integer> removeByFlowCode(String flowCode);
}
