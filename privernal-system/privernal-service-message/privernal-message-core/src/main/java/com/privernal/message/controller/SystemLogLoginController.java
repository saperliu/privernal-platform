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
import com.privernal.message.domain.SystemLogLogin;
import com.privernal.message.dto.SystemLogLoginDTO;
import com.privernal.message.filter.SystemLogLoginFilterDTO;
import com.privernal.message.service.ISystemLogLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录日志Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 登录日志的接口
 */
@Tag(name = "登录日志相关接口")
@RestController
public class SystemLogLoginController extends BaseController {
    @Autowired
    private ISystemLogLoginService systemLogLoginService;

    /**
     * 查询系统登录日志列表
     */
    @Operation(summary = "查询系统登录日志列表", description = "查询系统登录日志列表")
    @PreAuthorize(hasPermi = "message:systemLogLogin:list")
    @PostMapping("/back/message/systemLogLogin/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemLogLoginFilterDTO systemLogLogin) {
        startPage(systemLogLogin);
        List<SystemLogLoginDTO> list = systemLogLoginService.selectSystemLogLoginList(systemLogLogin);
        return getDataTable(list);
    }

    /**
     * 获取系统登录日志详细信息
     */
    @Operation(summary = "获取系统登录日志详细信息", description = "获取系统登录日志详细信息")
    @PreAuthorize(hasPermi = "message:systemLogLogin:query")
    @GetMapping(value = "/back/message/systemLogLogin/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemLogLoginService.selectSystemLogLoginDetailDTOById(id));
    }

    /**
     * 新增系统登录日志
     */
    @Operation(summary = "新增系统登录日志", description = "新增系统登录日志")
    @PreAuthorize(hasPermi = "message:systemLogLogin:add")
    @Log(title = "系统登录日志", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/message/systemLogLogin/save")
    public CommonResult<Object> add(@RequestBody SystemLogLoginDTO systemLogLoginDTO) {
        SystemLogLogin systemLogLogin = new SystemLogLogin();
        BeanUtils.copyProperties(systemLogLoginDTO, systemLogLogin);
        return CommonResult.ok(systemLogLoginService.insertSystemLogLogin(systemLogLogin));
    }

    /**
     * 修改系统登录日志
     */
    @Operation(summary = "修改系统登录日志", description = "修改系统登录日志")
    @PreAuthorize(hasPermi = "message:systemLogLogin:edit")
    @Log(title = "系统登录日志", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/message/systemLogLogin/edit")
    public CommonResult<Object> edit(@RequestBody SystemLogLoginDTO systemLogLoginDTO) {
        SystemLogLogin systemLogLogin = new SystemLogLogin();
        BeanUtils.copyProperties(systemLogLoginDTO, systemLogLogin);
        return CommonResult.ok(systemLogLoginService.updateSystemLogLogin(systemLogLogin));
    }

    /**
     * 删除系统登录日志
     */
    @Operation(summary = "删除系统登录日志", description = "删除系统登录日志")
    @PreAuthorize(hasPermi = "message:systemLogLogin:remove")
    @Log(title = "系统登录日志", operationType = OperationType.DELETE)
    @DeleteMapping("/back/message/systemLogLogin/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemLogLoginService.deleteSystemLogLoginByIds(ids));
    }

    /**
     * 新增登录日志
     */
    @Operation(summary = "新增登录日志", description = "新增登录日志")
    @ServiceRequest
    @PostMapping(value = "/inner/system/login/save")
    public CommonResult<Object> saveLogin(@RequestBody SystemLogLoginDTO systemLogLoginDTO) {
        SystemLogLogin systemLogLogin = new SystemLogLogin();
        BeanUtils.copyProperties(systemLogLoginDTO, systemLogLogin);
        return toAjax(systemLogLoginService.insertSystemLogLogin(systemLogLogin));
    }

    /**
     * 查询系统登录日志列表
     */
    @Operation(summary = "查询系统登录日志列表", description = "查询系统登录日志列表")
    @PreAuthorize(hasPermi = "message:systemLogLogin:list")
    @PostMapping("/app/message/systemLogLogin/list")
    public CommonResult<PageListDTO<Object>> appList(@RequestBody SystemLogLoginFilterDTO systemLogLogin) {
        startPage(systemLogLogin);
        systemLogLogin.setLoginName(SecurityUtils.getLoginName());
        systemLogLogin.setOrganizationId(SecurityUtils.getOrganizationId());
        systemLogLogin.setDepartmentId(SecurityUtils.getDepartmentId());
        List<SystemLogLoginDTO> list = systemLogLoginService.selectSystemLogLoginList(systemLogLogin);
        return getDataTable(list);
    }
}
