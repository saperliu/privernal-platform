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

import org.springframework.beans.BeanUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemRoleUserDept;
import com.privernal.system.dto.SystemRoleUserDeptDTO;
import com.privernal.system.filter.SystemRoleUserDeptFilterDTO;
import com.privernal.system.service.ISystemRoleUserDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 角色用户与部门关系Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色用户与部门关系的接口
 */
@Tag(name = "角色用户与部门关系相关接口")
@RestController
public class SystemRoleUserDeptController extends BaseController {
    @Autowired
    private ISystemRoleUserDeptService systemRoleUserDeptService;

    /**
     * 查询角色用户与部门关系列表
     */
    @Operation(summary = "查询角色用户与部门关系列表", description = "查询角色用户与部门关系列表")
    @PreAuthorize(hasPermi = "system:roleUserDept:list")
    @PostMapping("/back/system/roleUserDept/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemRoleUserDeptFilterDTO systemRoleUserDept) {
        startPage(systemRoleUserDept);
        List<SystemRoleUserDeptDTO> list = systemRoleUserDeptService.selectSystemRoleUserDeptDTOList(systemRoleUserDept);
        return getDataTable(list);
    }

    /**
     * 获取角色用户与部门关系详细信息
     */
    @Operation(summary = "获取角色用户与部门关系详细信息", description = "获取角色用户与部门关系详细信息")
    @PreAuthorize(hasPermi = "system:roleUserDept:query")
    @GetMapping(value = "/back/system/roleUserDept/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemRoleUserDeptService.selectSystemRoleUserDeptById(id));
    }

    /**
     * 新增角色用户与部门关系
     */
    @Operation(summary = "新增角色用户与部门关系", description = "新增角色用户与部门关系")
    @PreAuthorize(hasPermi = "system:roleUserDept:add")
    @Log(title = "角色用户与部门关系", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/roleUserDept/save")
    public CommonResult<Object> add(@RequestBody SystemRoleUserDeptDTO systemRoleUserDeptDTO) {
        SystemRoleUserDept systemRoleUserDept = new SystemRoleUserDept();
        BeanUtils.copyProperties(systemRoleUserDeptDTO, systemRoleUserDept);
        return toAjax(systemRoleUserDeptService.insertSystemRoleUserDept(systemRoleUserDept));
    }

    /**
     * 修改角色用户与部门关系
     */
    @Operation(summary = "修改角色用户与部门关系", description = "修改角色用户与部门关系")
    @PreAuthorize(hasPermi = "system:roleUserDept:edit")
    @Log(title = "角色用户与部门关系", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/roleUserDept/edit")
    public CommonResult<Object> edit(@RequestBody SystemRoleUserDeptDTO systemRoleUserDeptDTO) {
        SystemRoleUserDept systemRoleUserDept = new SystemRoleUserDept();
        BeanUtils.copyProperties(systemRoleUserDeptDTO, systemRoleUserDept);
        return toAjax(systemRoleUserDeptService.updateSystemRoleUserDept(systemRoleUserDept));
    }

    /**
     * 删除角色用户与部门关系
     */
    @Operation(summary = "删除角色用户与部门关系", description = "删除角色用户与部门关系")
    @PreAuthorize(hasPermi = "system:roleUserDept:remove")
    @Log(title = "角色用户与部门关系", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/roleUserDept/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemRoleUserDeptService.deleteSystemRoleUserDeptByIds(ids));
    }

    /**
     * 新增角色用户与部门关系
     */
    @Operation(summary = "新增角色用户与部门关系", description = "新增角色用户与部门关系")
    @PreAuthorize(hasPermi = "system:roleUserDept:add")
    @Log(title = "角色用户与部门关系", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/roleUserDept/batch/save")
    public CommonResult<Object> addBatch(@RequestBody SystemRoleUserDeptDTO systemRoleUserDeptDTO) {
        return toAjax(systemRoleUserDeptService.insertBatchSystemRoleUserDept(systemRoleUserDeptDTO));
    }

}
