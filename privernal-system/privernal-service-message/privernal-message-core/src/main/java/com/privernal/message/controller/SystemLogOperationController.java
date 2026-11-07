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

package com.privernal.message.controller;

import org.springframework.beans.BeanUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.annotation.ServiceRequest;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.message.domain.SystemLogOperation;
import com.privernal.message.dto.SystemLogOperationDTO;
import com.privernal.message.filter.SystemLogOperationFilterDTO;
import com.privernal.message.service.ISystemLogOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 操作日志的接口
 */
@Tag(name = "操作日志相关接口")
@RestController
public class  SystemLogOperationController extends BaseController {
    @Autowired
    private ISystemLogOperationService systemLogOperationService;

    /**
     * 查询系统操作日志列表
     */
    @Operation(summary = "查询系统操作日志列表", description = "查询系统操作日志列表")
    @PreAuthorize(hasPermi = "message:systemLogOperation:list")
    @PostMapping("/back/message/systemLogOperation/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemLogOperationFilterDTO systemLogOperation) {
        startPage(systemLogOperation);
        List<SystemLogOperationDTO> list = systemLogOperationService.selectSystemLogOperationList(systemLogOperation);
        return getDataTable(list);
    }

    /**
     * 获取系统操作日志详细信息
     */
    @Operation(summary = "获取系统操作日志详细信息", description = "获取系统操作日志详细信息")
    @PreAuthorize(hasPermi = "message:systemLogOperation:query")
    @GetMapping(value = "/back/message/systemLogOperation/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemLogOperationService.selectSystemLogOperationDetailDTOById(id));
    }

    /**
     * 新增系统操作日志
     */
    @Operation(summary = "新增系统操作日志", description = "新增系统操作日志")
    @PreAuthorize(hasPermi = "message:systemLogOperation:add")
    @Log(title = "系统操作日志", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/message/systemLogOperation/save")
    public CommonResult<Object> add(@RequestBody SystemLogOperationDTO systemLogOperationDTO) {
        SystemLogOperation systemLogOperation = new SystemLogOperation();
        BeanUtils.copyProperties(systemLogOperationDTO, systemLogOperation);
        return CommonResult.ok(systemLogOperationService.insertSystemLogOperation(systemLogOperation));
    }

    /**
     * 修改系统操作日志
     */
    @Operation(summary = "修改系统操作日志", description = "修改系统操作日志")
    @PreAuthorize(hasPermi = "message:systemLogOperation:edit")
    @Log(title = "系统操作日志", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/message/systemLogOperation/edit")
    public CommonResult<Object> edit(@RequestBody SystemLogOperationDTO systemLogOperationDTO) {
        SystemLogOperation systemLogOperation = new SystemLogOperation();
        BeanUtils.copyProperties(systemLogOperationDTO, systemLogOperation);
        return CommonResult.ok(systemLogOperationService.updateSystemLogOperation(systemLogOperation));
    }

    /**
     * 删除系统操作日志
     */
    @Operation(summary = "删除系统操作日志", description = "删除系统操作日志")
    @PreAuthorize(hasPermi = "message:systemLogOperation:remove")
    @Log(title = "系统操作日志", operationType = OperationType.DELETE)
    @DeleteMapping("/back/message/systemLogOperation/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemLogOperationService.deleteSystemLogOperationByIds(ids));
    }

    /**
     * 新增操作日志
     */
    @Operation(summary = "新增操作日志", description = "新增操作日志")
    @ServiceRequest
    @PostMapping(value = "/inner/system/operation/save")
    public CommonResult<Object> innerAdd(@RequestBody SystemLogOperationDTO systemLogOperationDTO) {
        SystemLogOperation systemLogOperation = new SystemLogOperation();
        BeanUtils.copyProperties(systemLogOperationDTO, systemLogOperation);
        return toAjax(systemLogOperationService.insertSystemLogOperation(systemLogOperation));
    }
}
