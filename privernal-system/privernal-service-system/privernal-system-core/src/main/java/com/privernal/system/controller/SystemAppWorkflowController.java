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

package com.privernal.system.controller;

import com.privernal.common.core.constant.DataConstants;
import org.springframework.beans.BeanUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.annotation.ServiceRequest;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemAppWorkflow;
import com.privernal.system.dto.SystemAppWorkflowDTO;
import com.privernal.system.filter.SystemAppWorkflowFilterDTO;
import com.privernal.system.service.ISystemAppWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 应用关联的流程单Controller
 *
 * @author saperliu
 * @date 2024-07-19
 * @Description 应用关联的流程单的接口
 */
@Tag(name = "应用关联的流程单相关接口")
@RestController
@Slf4j
public class SystemAppWorkflowController extends BaseController {
    @Autowired
    private ISystemAppWorkflowService systemAppWorkflowService;

    /**
     * 查询应用关联的流程单列表
     */
    @Operation(summary = "查询应用关联的流程单列表", description = "查询应用关联的流程单列表")
    @PreAuthorize(hasPermi = "system:appWorkflow:list")
    @PostMapping("/back/system/appWorkflow/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemAppWorkflowFilterDTO systemAppWorkflow) {
        startPage(systemAppWorkflow);
        List<SystemAppWorkflowDTO> list = systemAppWorkflowService.selectSystemAppWorkflowList(systemAppWorkflow);
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        return tableResult;
    }

    /**
     * 获取应用关联的流程单详细信息
     */
    @Operation(summary = "获取应用关联的流程单详细信息", description = "获取应用关联的流程单详细信息")
    @PreAuthorize(hasPermi = "system:appWorkflow:query")
    @GetMapping(value = "/back/system/appWorkflow/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemAppWorkflowService.selectSystemAppWorkflowDetailDTOById(id));
    }

    /**
     * 新增应用关联的流程单
     */
    @Operation(summary = "新增应用关联的流程单", description = "新增应用关联的流程单")
    @PreAuthorize(hasPermi = "system:appWorkflow:add")
    @Log(title = "应用关联的流程单", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/appWorkflow/save")
    public CommonResult<Object> add(@RequestBody SystemAppWorkflowDTO systemAppWorkflowDTO) {
        SystemAppWorkflow systemAppWorkflow = new SystemAppWorkflow();
        BeanUtils.copyProperties(systemAppWorkflowDTO, systemAppWorkflow, DataConstants.IGNORED_ON_COPY);
        return CommonResult.ok(systemAppWorkflowService.insertSystemAppWorkflow(systemAppWorkflow));
    }

    /**
     * 修改应用关联的流程单
     */
    @Operation(summary = "修改应用关联的流程单", description = "修改应用关联的流程单")
    @PreAuthorize(hasPermi = "system:appWorkflow:edit")
    @Log(title = "应用关联的流程单", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/appWorkflow/edit")
    public CommonResult<Object> edit(@RequestBody SystemAppWorkflowDTO systemAppWorkflowDTO) {
        SystemAppWorkflow systemAppWorkflow = new SystemAppWorkflow();
        BeanUtils.copyProperties(systemAppWorkflowDTO, systemAppWorkflow, DataConstants.IGNORED_ON_COPY);
        return CommonResult.ok(systemAppWorkflowService.updateSystemAppWorkflow(systemAppWorkflow));
    }

    /**
     * 删除应用关联的流程单
     */
    @Operation(summary = "删除应用关联的流程单", description = "删除应用关联的流程单")
    @PreAuthorize(hasPermi = "system:appWorkflow:remove")
    @Log(title = "应用关联的流程单", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/appWorkflow/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemAppWorkflowService.deleteSystemAppWorkflowByIds(ids));
    }

    /**
     * 查询应用关联的流程单列表
     */
    @Operation(summary = "查询应用关联的流程单列表", description = "查询应用关联的流程单列表")
    @ServiceRequest
    @GetMapping("/inner/system/appWorkflow/{appId}")
    public CommonResult<Object> innerList(@PathVariable("appId") String appId) {
        SystemAppWorkflowFilterDTO systemAppWorkflow = new SystemAppWorkflowFilterDTO();
        systemAppWorkflow.setAppId(appId);
        List<SystemAppWorkflowDTO> list = systemAppWorkflowService.selectSystemAppWorkflowList(systemAppWorkflow);
        // 获取list里的所有workflowId
        List<String> workflowIds = list.stream().map(SystemAppWorkflowDTO::getWorkflowFormId).toList();
        return CommonResult.ok(workflowIds);
    }

}
