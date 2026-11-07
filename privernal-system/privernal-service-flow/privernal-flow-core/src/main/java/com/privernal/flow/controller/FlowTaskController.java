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

package com.privernal.flow.controller;

import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.flow.dto.FlowAdjustTaskDTO;
import com.privernal.flow.dto.FlowTaskHandleDTO;
import com.privernal.flow.dto.FlowTaskOptionDTO;
import com.privernal.flow.filter.FlowTaskFilterDTO;
import com.privernal.flow.service.IFlowTaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 流程任务Controller（待办、已办、审批、驳回、委托、转办、加减签、跳转、拿回、签收、调整）
 *
 * @author saperliu
 */
@Tag(name = "流程任务相关接口")
@RestController
@RequestMapping("/back/flow/task")
public class FlowTaskController extends BaseController {

    @Autowired
    private IFlowTaskService flowTaskService;

    /**
     * 待办任务分页
     */
    @Operation(summary = "待办任务分页")
    @PreAuthorize(hasPermi = "flow:task:list")
    @PostMapping("/todo/list")
    public CommonResult<PageListDTO<Object>> todoList(@RequestBody FlowTaskFilterDTO filter) {
        Page<Task> page = flowTaskService.todoPage(filter);
        return CommonResult.ok(new PageListDTO<Object>(page.getRecords(), (int) page.getTotal()));
    }

    /**
     * 已办任务分页
     */
    @Operation(summary = "已办任务分页")
    @PreAuthorize(hasPermi = "flow:task:list")
    @PostMapping("/done/list")
    public CommonResult<PageListDTO<Object>> doneList(@RequestBody FlowTaskFilterDTO filter) {
        Page<HisTask> page = flowTaskService.donePage(filter);
        return CommonResult.ok(new PageListDTO<Object>(page.getRecords(), (int) page.getTotal()));
    }

    /**
     * 任务数量统计（todo 待办 / done 已办 / start 我发起的）
     */
    @Operation(summary = "任务数量统计")
    @PreAuthorize(hasPermi = "flow:task:list")
    @GetMapping("/count")
    public CommonResult<Map<String, Long>> count() {
        return CommonResult.ok(flowTaskService.counts());
    }

    /**
     * 任务办理选项（驳回/跳转可选节点、减签可选办理人）
     * 权限与待办列表一致（flow:task:list），服务层校验仅任务办理人可查
     */
    @Operation(summary = "任务办理选项（驳回/跳转可选节点、减签可选办理人）")
    @PreAuthorize(hasPermi = "flow:task:list")
    @GetMapping("/options/{taskId}")
    public CommonResult<FlowTaskOptionDTO> options(@PathVariable("taskId") Long taskId) {
        return CommonResult.ok(flowTaskService.taskOptions(taskId));
    }

    /**
     * 审批通过
     */
    @Operation(summary = "审批通过")
    @PreAuthorize(hasPermi = "flow:task:approve")
    @Log(title = "流程任务-通过", operationType = OperationType.UPDATE)
    @PutMapping("/approve")
    public CommonResult<Instance> approve(@RequestBody FlowTaskHandleDTO handleDTO) {
        return CommonResult.ok(flowTaskService.approve(handleDTO));
    }

    /**
     * 驳回（rejectType：LAST 上一节点 / START 发起节点 / TARGET 指定节点）
     */
    @Operation(summary = "驳回任务")
    @PreAuthorize(hasPermi = "flow:task:reject")
    @Log(title = "流程任务-驳回", operationType = OperationType.UPDATE)
    @PutMapping("/reject")
    public CommonResult<Instance> reject(@RequestBody FlowTaskHandleDTO handleDTO) {
        return CommonResult.ok(flowTaskService.reject(handleDTO));
    }

    /**
     * 转办（更换办理人）
     */
    @Operation(summary = "转办任务")
    @PreAuthorize(hasPermi = "flow:task:transfer")
    @Log(title = "流程任务-转办", operationType = OperationType.UPDATE)
    @PutMapping("/transfer")
    public CommonResult<Object> transfer(@RequestBody FlowTaskHandleDTO handleDTO) {
        return toAjax(flowTaskService.transfer(handleDTO));
    }

    /**
     * 委托（委派他人办理，办理后回到委托人）
     */
    @Operation(summary = "委托任务")
    @PreAuthorize(hasPermi = "flow:task:delegate")
    @Log(title = "流程任务-委托", operationType = OperationType.UPDATE)
    @PutMapping("/delegate")
    public CommonResult<Object> depute(@RequestBody FlowTaskHandleDTO handleDTO) {
        return toAjax(flowTaskService.depute(handleDTO));
    }

    /**
     * 加签（增加办理人）
     */
    @Operation(summary = "加签")
    @PreAuthorize(hasPermi = "flow:task:addSign")
    @Log(title = "流程任务-加签", operationType = OperationType.UPDATE)
    @PutMapping("/add-sign")
    public CommonResult<Object> addSignature(@RequestBody FlowTaskHandleDTO handleDTO) {
        return toAjax(flowTaskService.addSignature(handleDTO));
    }

    /**
     * 减签（减少办理人）
     */
    @Operation(summary = "减签")
    @PreAuthorize(hasPermi = "flow:task:addSign")
    @Log(title = "流程任务-减签", operationType = OperationType.UPDATE)
    @PutMapping("/reduce-sign")
    public CommonResult<Object> reductionSignature(@RequestBody FlowTaskHandleDTO handleDTO) {
        return toAjax(flowTaskService.reductionSignature(handleDTO));
    }

    /**
     * 跳转（任意跳转到目标节点）
     */
    @Operation(summary = "任务跳转")
    @PreAuthorize(hasPermi = "flow:task:jump")
    @Log(title = "流程任务-跳转", operationType = OperationType.UPDATE)
    @PutMapping("/jump")
    public CommonResult<Instance> jump(@RequestBody FlowTaskHandleDTO handleDTO) {
        return CommonResult.ok(flowTaskService.jump(handleDTO));
    }

    /**
     * 暂存任务（不流转）
     */
    @Operation(summary = "暂存任务")
    @PreAuthorize(hasPermi = "flow:task:approve")
    @Log(title = "流程任务", operationType = OperationType.UPDATE)
    @PutMapping("/pending")
    public CommonResult<Instance> pending(@RequestBody FlowTaskHandleDTO handleDTO) {
        return CommonResult.ok(flowTaskService.pending(handleDTO));
    }

    /**
     * 拿回（已办任务取回到待办）
     */
    @Operation(summary = "拿回任务")
    @PreAuthorize(hasPermi = "flow:task:approve")
    @Log(title = "流程任务-拿回", operationType = OperationType.UPDATE)
    @PutMapping("/retrieve")
    public CommonResult<Instance> retrieve(@RequestBody FlowTaskHandleDTO handleDTO) {
        return CommonResult.ok(flowTaskService.retrieve(handleDTO));
    }

    /**
     * 任务签收（将任务分配给当前用户）
     */
    @Operation(summary = "任务签收")
    @PreAuthorize(hasPermi = "flow:task:claim")
    @Log(title = "流程任务-签收", operationType = OperationType.UPDATE)
    @PutMapping("/claim")
    public CommonResult<Instance> claim(@RequestBody FlowTaskHandleDTO handleDTO) {
        return CommonResult.ok(flowTaskService.claim(handleDTO));
    }

    /**
     * 更换审批人
     */
    @Operation(summary = "更换审批人（直接更换，不保留原办理人）")
    @PreAuthorize(hasPermi = "flow:task:transfer")
    @Log(title = "流程任务-更换审批人", operationType = OperationType.UPDATE)
    @PutMapping("/change-person")
    public CommonResult<Instance> changePerson(@RequestBody FlowTaskHandleDTO handleDTO) {
        return CommonResult.ok(flowTaskService.changePerson(handleDTO));
    }

    /**
     * 管理员调整任务（更换审批人或跳转到指定节点）
     */
    @Operation(summary = "管理员调整任务")
    @PreAuthorize(hasPermi = "flow:task:adjust")
    @Log(title = "流程任务-管理员调整", operationType = OperationType.UPDATE)
    @PutMapping("/adjust")
    public CommonResult<Instance> adjust(@RequestBody FlowAdjustTaskDTO adjustDTO) {
        return CommonResult.ok(flowTaskService.adjust(adjustDTO));
    }
}
