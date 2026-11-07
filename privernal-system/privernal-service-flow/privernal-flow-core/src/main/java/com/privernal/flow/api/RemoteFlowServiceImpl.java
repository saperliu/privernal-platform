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

package com.privernal.flow.api;

import com.privernal.common.web.domain.CommonResult;
import com.privernal.flow.dto.FlowAdjustTaskDTO;
import com.privernal.flow.dto.FlowFormPermissionDTO;
import com.privernal.flow.dto.FlowInstanceStatusDTO;
import com.privernal.flow.dto.FlowStartDTO;
import com.privernal.flow.dto.FlowTaskHandleDTO;
import com.privernal.flow.feign.RemoteFlowService;
import com.privernal.flow.service.IFlowDefinitionService;
import com.privernal.flow.service.IFlowFormService;
import com.privernal.flow.service.IFlowInstanceService;
import com.privernal.flow.service.IFlowTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dromara.warm.flow.core.entity.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流内部服务实现（单体本地 Bean，同时暴露 /inner/flow 端点）
 *
 * @author saperliu
 */
@Tag(name = "工作流内部接口")
@RestController
public class RemoteFlowServiceImpl implements RemoteFlowService {

    @Autowired
    private IFlowInstanceService flowInstanceService;

    @Autowired
    private IFlowTaskService flowTaskService;

    @Autowired
    private IFlowDefinitionService flowDefinitionService;

    @Autowired
    private IFlowFormService flowFormService;

    @Operation(summary = "启动流程（内部）")
    @PostMapping("/inner/flow/instance/start")
    @Override
    public CommonResult<FlowInstanceStatusDTO> start(@RequestBody FlowStartDTO startDTO) {
        return CommonResult.ok(flowInstanceService.start(startDTO));
    }

    @Operation(summary = "按业务id查询流程状态（内部）")
    @GetMapping("/inner/flow/instance/status/{businessId}")
    @Override
    public CommonResult<FlowInstanceStatusDTO> getStatusByBusinessId(@PathVariable("businessId") String businessId) {
        return CommonResult.ok(flowInstanceService.getStatusByBusinessId(businessId));
    }

    @Operation(summary = "业务数据流程参与人判定（内部）")
    @GetMapping("/inner/flow/instance/participant")
    @Override
    public CommonResult<Boolean> isBusinessDataParticipant(@RequestParam("tableCode") String tableCode,
                                                           @RequestParam("businessId") String businessId) {
        return CommonResult.ok(flowInstanceService.isCurrentUserParticipant(tableCode, businessId));
    }

    @Operation(summary = "运行时表单权限（内部）")
    @GetMapping("/inner/flow/form/permission")
    @Override
    public CommonResult<FlowFormPermissionDTO> getFormPermission(@RequestParam("tableCode") String tableCode,
                                                                 @RequestParam("businessId") String businessId) {
        return CommonResult.ok(flowFormService.getRuntimePermission(tableCode, businessId));
    }

    @Operation(summary = "审批通过（内部）")
    @PutMapping("/inner/flow/task/approve")
    @Override
    public CommonResult<FlowInstanceStatusDTO> approve(@RequestBody FlowTaskHandleDTO handleDTO) {
        Instance instance = flowTaskService.approve(handleDTO);
        return CommonResult.ok(flowInstanceService.getStatusByInstanceId(instance.getId()));
    }

    @Operation(summary = "驳回（内部，rejectType：LAST/START/TARGET）")
    @PutMapping("/inner/flow/task/reject")
    @Override
    public CommonResult<FlowInstanceStatusDTO> reject(@RequestBody FlowTaskHandleDTO handleDTO) {
        Instance instance = flowTaskService.reject(handleDTO);
        return CommonResult.ok(flowInstanceService.getStatusByInstanceId(instance.getId()));
    }

    @Operation(summary = "拿回已办任务（内部，对应 wfs undo）")
    @PutMapping("/inner/flow/task/retrieve")
    @Override
    public CommonResult<FlowInstanceStatusDTO> retrieve(@RequestBody FlowTaskHandleDTO handleDTO) {
        Instance instance = flowTaskService.retrieve(handleDTO);
        return CommonResult.ok(flowInstanceService.getStatusByInstanceId(instance.getId()));
    }

    @Operation(summary = "转办（内部，对应 wfs assignee）")
    @PutMapping("/inner/flow/task/transfer")
    @Override
    public CommonResult<Boolean> transfer(@RequestBody FlowTaskHandleDTO handleDTO) {
        return CommonResult.ok(flowTaskService.transfer(handleDTO));
    }

    @Operation(summary = "终止流程实例（内部）")
    @PutMapping("/inner/flow/instance/terminate/{instanceId}")
    @Override
    public CommonResult<FlowInstanceStatusDTO> terminate(@PathVariable("instanceId") Long instanceId,
                                                         @RequestParam(value = "message", required = false) String message,
                                                         @RequestParam(value = "handler", required = false) String handler) {
        return CommonResult.ok(flowInstanceService.terminate(instanceId, message, handler));
    }

    @Operation(summary = "重启流程实例（内部）")
    @PutMapping("/inner/flow/instance/restart/{instanceId}")
    @Override
    public CommonResult<FlowInstanceStatusDTO> restart(@PathVariable("instanceId") Long instanceId,
                                                       @RequestParam(value = "message", required = false) String message) {
        return CommonResult.ok(flowInstanceService.restart(instanceId, message));
    }

    @Operation(summary = "管理员调整任务（内部）")
    @PutMapping("/inner/flow/task/adjust")
    @Override
    public CommonResult<FlowInstanceStatusDTO> adjust(@RequestBody FlowAdjustTaskDTO adjustDTO) {
        Instance instance = flowTaskService.adjust(adjustDTO);
        return CommonResult.ok(flowInstanceService.getStatusByInstanceId(instance.getId()));
    }

    @Operation(summary = "按实例id查询流程状态（内部）")
    @GetMapping("/inner/flow/instance/statusById/{instanceId}")
    @Override
    public CommonResult<FlowInstanceStatusDTO> getStatusByInstanceId(@PathVariable("instanceId") Long instanceId) {
        return CommonResult.ok(flowInstanceService.getStatusByInstanceId(instanceId));
    }

    @Operation(summary = "按流程编码删除全部版本流程（内部，动态表单删除时调用）")
    @DeleteMapping("/inner/flow/definition/flowCode/{flowCode}")
    @Override
    public CommonResult<Integer> removeByFlowCode(@PathVariable("flowCode") String flowCode) {
        return CommonResult.ok(flowDefinitionService.removeByFlowCode(flowCode));
    }
}
