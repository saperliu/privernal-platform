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
import com.privernal.flow.dto.FlowInstanceStatusDTO;
import com.privernal.flow.dto.FlowNodeInfoDTO;
import com.privernal.flow.dto.FlowRestartDTO;
import com.privernal.flow.dto.FlowStartDTO;
import com.privernal.flow.filter.FlowInstanceFilterDTO;
import com.privernal.flow.service.IFlowInstanceService;
import com.privernal.flow.service.IFlowTaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程实例Controller（启动、撤销、终止、查询、重启、节点列表）
 *
 * @author saperliu
 */
@Tag(name = "流程实例相关接口")
@RestController
@RequestMapping("/back/flow/instance")
public class FlowInstanceController extends BaseController {

    @Autowired
    private IFlowInstanceService flowInstanceService;

    @Autowired
    private IFlowTaskService flowTaskService;

    /**
     * 启动流程
     */
    @Operation(summary = "启动流程")
    @PreAuthorize(hasPermi = "flow:instance:list")
    @Log(title = "流程实例", operationType = OperationType.INSERT)
    @PostMapping("/save")
    public CommonResult<FlowInstanceStatusDTO> start(@RequestBody FlowStartDTO startDTO) {
        return CommonResult.ok(flowInstanceService.start(startDTO));
    }

    /**
     * 我发起的流程分页
     */
    @Operation(summary = "我发起的流程分页")
    @PreAuthorize(hasPermi = "flow:instance:list")
    @PostMapping("/my/list")
    public CommonResult<PageListDTO<Object>> myList(@RequestBody FlowInstanceFilterDTO filter) {
        Page<Instance> page = flowInstanceService.pageInstances(filter, true);
        return CommonResult.ok(new PageListDTO<Object>(page.getRecords(), (int) page.getTotal()));
    }

    /**
     * 全部流程实例分页（管理视角）
     */
    @Operation(summary = "全部流程实例分页")
    @PreAuthorize(hasPermi = "flow:instance:list")
    @PostMapping("/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody FlowInstanceFilterDTO filter) {
        Page<Instance> page = flowInstanceService.pageInstances(filter, false);
        return CommonResult.ok(new PageListDTO<Object>(page.getRecords(), (int) page.getTotal()));
    }

    /**
     * 按业务数据id查询实例
     * 表单数据列表的「流程」按钮按登录态即可查询（flow 菜单未绑定应用，flow:instance:list 对表单用户不可见）
     */
    @Operation(summary = "按业务数据id查询流程实例")
    @GetMapping("/byBusiness/{businessId}")
    public CommonResult<FlowInstanceStatusDTO> getByBusinessId(@PathVariable("businessId") String businessId) {
        return CommonResult.ok(flowInstanceService.getStatusByBusinessId(businessId));
    }

    /**
     * 终止（停止）流程
     */
    @Operation(summary = "终止流程")
    @PreAuthorize(hasPermi = "flow:instance:terminate")
    @Log(title = "流程实例", operationType = OperationType.UPDATE)
    @PutMapping("/terminate/{instanceId}")
    public CommonResult<FlowInstanceStatusDTO> terminate(@PathVariable("instanceId") Long instanceId,
                                                         @RequestParam(value = "message", required = false) String message) {
        return CommonResult.ok(flowInstanceService.terminate(instanceId, message));
    }

    /**
     * 撤销流程（发起人撤回）
     */
    @Operation(summary = "撤销流程")
    @PreAuthorize(hasPermi = "flow:instance:revoke")
    @Log(title = "流程实例", operationType = OperationType.UPDATE)
    @PutMapping("/revoke/{instanceId}")
    public CommonResult<FlowInstanceStatusDTO> revoke(@PathVariable("instanceId") Long instanceId,
                                                      @RequestParam(value = "message", required = false) String message) {
        return CommonResult.ok(flowInstanceService.revoke(instanceId, message));
    }

    /**
     * 激活实例
     */
    @Operation(summary = "激活流程实例")
    @PreAuthorize(hasPermi = "flow:instance:terminate")
    @Log(title = "流程实例", operationType = OperationType.UPDATE)
    @PutMapping("/active/{instanceId}")
    public CommonResult<Object> active(@PathVariable("instanceId") Long instanceId) {
        return toAjax(flowInstanceService.active(instanceId));
    }

    /**
     * 挂起实例
     */
    @Operation(summary = "挂起流程实例")
    @PreAuthorize(hasPermi = "flow:instance:terminate")
    @Log(title = "流程实例", operationType = OperationType.UPDATE)
    @PutMapping("/unactive/{instanceId}")
    public CommonResult<Object> unActive(@PathVariable("instanceId") Long instanceId) {
        return toAjax(flowInstanceService.unActive(instanceId));
    }

    /**
     * 实例审批记录（登录态即可查，供表单数据列表「流程」按钮展示流转记录）
     */
    @Operation(summary = "实例审批记录")
    @GetMapping("/record/{instanceId}")
    public CommonResult<List<HisTask>> record(@PathVariable("instanceId") Long instanceId) {
        return CommonResult.ok(flowTaskService.records(instanceId));
    }

    /**
     * 重启流程
     */
    @Operation(summary = "重启流程（终止旧实例并基于相同业务数据启动新实例）")
    @PreAuthorize(hasPermi = "flow:instance:restart")
    @Log(title = "流程实例", operationType = OperationType.UPDATE)
    @PostMapping("/restart")
    public CommonResult<FlowInstanceStatusDTO> restart(@RequestBody FlowRestartDTO restartDTO) {
        return CommonResult.ok(flowInstanceService.restart(restartDTO.getInstanceId(), restartDTO.getMessage()));
    }

    /**
     * 获取流程节点信息列表（流程图高亮）
     */
    @Operation(summary = "获取流程节点信息列表（流程图高亮回显）")
    @PreAuthorize(hasPermi = "flow:instance:list")
    @GetMapping("/nodes/{instanceId}")
    public CommonResult<List<FlowNodeInfoDTO>> getInstanceNodes(@PathVariable("instanceId") Long instanceId) {
        return CommonResult.ok(flowInstanceService.getInstanceNodes(instanceId));
    }
}
