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
import com.privernal.system.domain.SystemRole;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.dto.SystemRoleDTO;
import com.privernal.system.dto.SystemRoleMenuDTO;
import com.privernal.system.dto.SystemRoleUserDTO;
import com.privernal.system.filter.SystemRoleFilterDTO;
import com.privernal.system.filter.SystemRoleUserFilterDTO;
import com.privernal.system.service.ISystemRoleService;
import com.privernal.system.service.ISystemRoleUserDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色Controller
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色的接口
 */
@Tag(name = "角色相关接口")
@RestController
public class SystemRoleController extends BaseController {
    @Autowired
    private ISystemRoleService systemRoleService;
    @Autowired
    private ISystemRoleUserDeptService systemRoleUserDeptService;

    /**
     * 查询角色列表
     */
    @Operation(summary = "查询角色列表", description = "查询角色列表")
    @PreAuthorize(hasPermi = "system:role:list")
    @PostMapping("/back/system/role/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemRoleFilterDTO systemRole) {
        startPage(systemRole);
        List<SystemRole> list = systemRoleService.selectSystemRoleList(systemRole);
        List<SystemRoleDTO> dtoList = new ArrayList<>();
        for (SystemRole obj : list) {
            SystemRoleDTO dto = new SystemRoleDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 查询角色列表
     */
    @Operation(summary = "查询角色列表", description = "查询角色列表")
    @PreAuthorize(hasPermi = "system:role:list")
    @PostMapping("/back/system/role/list/dto")
    public CommonResult<PageListDTO<Object>> listDTO(@RequestBody SystemRoleFilterDTO systemRole) {
        startPage(systemRole);
        List<SystemRoleDTO> list = systemRoleService.selectSystemRoleDTOList(systemRole);
        return getDataTable(list);
    }

    /**
     * 获取角色详细信息
     */
    @Operation(summary = "获取角色详细信息", description = "获取角色详细信息")
    @PreAuthorize(hasPermi = "system:role:query")
    @GetMapping(value = "/back/system/role/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemRoleService.selectSystemRoleById(id));
    }

    /**
     * 新增角色
     */
    @Operation(summary = "新增角色", description = "新增角色")
    @PreAuthorize(hasPermi = "system:role:add")
    @Log(title = "角色", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/role/save")
    public CommonResult<Object> add(@RequestBody SystemRoleDTO systemRoleDTO) {
        SystemRole systemRole = new SystemRole();
        BeanUtils.copyProperties(systemRoleDTO, systemRole);
        return toAjax(systemRoleService.insertSystemRole(systemRole));
    }

    /**
     * 修改角色
     */
    @Operation(summary = "修改角色", description = "修改角色")
    @PreAuthorize(hasPermi = "system:role:edit")
    @Log(title = "角色", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/role/edit")
    public CommonResult<Object> edit(@RequestBody SystemRoleDTO systemRoleDTO) {
        SystemRole systemRole = new SystemRole();
        BeanUtils.copyProperties(systemRoleDTO, systemRole);
        return toAjax(systemRoleService.updateSystemRole(systemRole));
    }

    /**
     * 删除角色
     */
    @Operation(summary = "删除角色", description = "删除角色")
    @PreAuthorize(hasPermi = "system:role:remove")
    @Log(title = "角色", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/role/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemRoleService.deleteSystemRoleByIds(ids));
    }

    /**
     * 查询角色里的用户
     */
    @Operation(summary = "查询角色里的用户", description = "查询角色里的用户")
    @PreAuthorize(hasPermi = "system:role:list")
    @PostMapping("/back/system/role/user/list")
    public CommonResult<PageListDTO<Object>> listUser(@RequestBody SystemRoleUserFilterDTO roleUserFilterDTO) {
        startPage(roleUserFilterDTO);
        List<SystemRoleUserDTO> list = systemRoleService.selectSystemUserByRoleList(roleUserFilterDTO.getRoleId(), roleUserFilterDTO.getNickName());
        list.forEach(obj -> {
            if (obj.getUserId() != null) {
                List<SystemDepartmentDTO> listDept = systemRoleUserDeptService.selectSystemDeptByUserIdAndRoleId(obj.getUserId(), obj.getRoleId());
                obj.setDepartmentDTOList(listDept);
            }
        });

        return getDataTable(list);
    }

    /**
     * 查询角色里的菜单功能
     *
     * @param roleId 角色
     * @param appId  应用
     */
    @Operation(summary = "查询角色里的菜单功能", description = "查询角色里的菜单功能")
    @PreAuthorize(hasPermi = "system:role:list")
    @GetMapping("/back/system/role/menu/list/{roleId}/{appId}")
    public CommonResult<Object> listMenu(@PathVariable("roleId") String roleId, @PathVariable("appId") String appId) {
        List<SystemRoleMenuDTO> list = systemRoleService.selectSystemMenuByRoleList(roleId, appId);
        return CommonResult.ok(list);
    }
}
