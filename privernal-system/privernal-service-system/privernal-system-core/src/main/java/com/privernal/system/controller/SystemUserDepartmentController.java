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
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemUserDepartment;
import com.privernal.system.dto.SystemUserDepartmentDTO;
import com.privernal.system.filter.SystemUserDepartmentFilterDTO;
import com.privernal.system.service.ISystemUserDepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户兼职部门Controller
 *
 * @author saperliu
 * @date 2023-10-10
 * @Description 用户兼职部门的接口
 */
@Tag(name = "用户兼职部门相关接口")
@RestController
public class SystemUserDepartmentController extends BaseController {
    @Autowired
    private ISystemUserDepartmentService systemUserDepartmentService;

    /**
     * 查询用户兼职部门列表
     */
    @Operation(summary = "查询用户兼职部门列表", description = "查询用户兼职部门列表")
    @PreAuthorize(hasPermi = "system:userDepartment:list")
    @PostMapping("/back/system/userDepartment/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemUserDepartmentFilterDTO systemUserDepartment) {
        startPage(systemUserDepartment);
        List<SystemUserDepartmentDTO> list = systemUserDepartmentService.selectSystemUserDepartmentList(systemUserDepartment);
        List<SystemUserDepartmentDTO> dtoList = new ArrayList<>();
        for (SystemUserDepartmentDTO obj : list) {
            SystemUserDepartmentDTO dto = new SystemUserDepartmentDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        return getDataTable(dtoList);
    }

    /**
     * 获取用户兼职部门详细信息
     */
    @Operation(summary = "获取用户兼职部门详细信息", description = "获取用户兼职部门详细信息")
    @PreAuthorize(hasPermi = "system:userDepartment:query")
    @GetMapping(value = "/back/system/userDepartment/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemUserDepartmentService.selectSystemUserDepartmentById(id));
    }

    /**
     * 新增用户兼职部门
     */
    @Operation(summary = "新增用户兼职部门", description = "新增用户兼职部门")
    @PreAuthorize(hasPermi = "system:userDepartment:add")
    @Log(title = "用户兼职部门", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/userDepartment/save")
    public CommonResult<Object> add(@RequestBody SystemUserDepartmentDTO systemUserDepartmentDTO) {
        SystemUserDepartment systemUserDepartment = new SystemUserDepartment();
        BeanUtils.copyProperties(systemUserDepartmentDTO, systemUserDepartment);
        return toAjax(systemUserDepartmentService.insertSystemUserDepartment(systemUserDepartment));
    }

    /**
     * 修改用户兼职部门
     */
    @Operation(summary = "修改用户兼职部门", description = "修改用户兼职部门")
    @PreAuthorize(hasPermi = "system:userDepartment:edit")
    @Log(title = "用户兼职部门", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/userDepartment/edit")
    public CommonResult<Object> edit(@RequestBody SystemUserDepartmentDTO systemUserDepartmentDTO) {
        SystemUserDepartment systemUserDepartment = new SystemUserDepartment();
        BeanUtils.copyProperties(systemUserDepartmentDTO, systemUserDepartment);
        return toAjax(systemUserDepartmentService.updateSystemUserDepartment(systemUserDepartment));
    }

    /**
     * 删除用户兼职部门
     */
    @Operation(summary = "删除用户兼职部门", description = "删除用户兼职部门")
    @PreAuthorize(hasPermi = "system:userDepartment:remove")
    @Log(title = "用户兼职部门", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/userDepartment/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemUserDepartmentService.deleteSystemUserDepartmentByIds(ids));
    }

}
