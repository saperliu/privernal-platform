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
import com.privernal.system.domain.SystemUserGroup;
import com.privernal.system.dto.SystemUserGroupDTO;
import com.privernal.system.dto.SystemUserGroupUserDTO;
import com.privernal.system.filter.SystemUserGroupFilterDTO;
import com.privernal.system.filter.SystemUserGroupUserFilterDTO;
import com.privernal.system.service.ISystemUserGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户组Controller
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户组的接口
 */
@Tag(name = "用户组相关接口")
@RestController
public class SystemUserGroupController extends BaseController {
    @Autowired
    private ISystemUserGroupService systemUserGroupService;

    /**
     * 查询用户组列表
     */
    @Operation(summary = "查询用户组列表", description = "查询用户组列表")
    @PreAuthorize(hasPermi = "system:userGroup:list")
    @PostMapping("/back/system/userGroup/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemUserGroupFilterDTO systemUserGroup) {
        startPage(systemUserGroup);
        List<SystemUserGroup> list = systemUserGroupService.selectSystemUserGroupList(systemUserGroup);
        List<SystemUserGroupDTO> dtoList = new ArrayList<>();
        for (SystemUserGroup obj : list) {
            SystemUserGroupDTO dto = new SystemUserGroupDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取用户组详细信息
     */
    @Operation(summary = "获取用户组详细信息", description = "获取用户组详细信息")
    @PreAuthorize(hasPermi = "system:userGroup:query")
    @GetMapping(value = "/back/system/userGroup/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemUserGroupService.selectSystemUserGroupById(id));
    }

    /**
     * 新增用户组
     */
    @Operation(summary = "新增用户组", description = "新增用户组")
    @PreAuthorize(hasPermi = "system:userGroup:add")
    @Log(title = "用户组", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/userGroup/save")
    public CommonResult<Object> add(@RequestBody SystemUserGroupDTO systemUserGroupDTO) {
        SystemUserGroup systemUserGroup = new SystemUserGroup();
        BeanUtils.copyProperties(systemUserGroupDTO, systemUserGroup);
        return toAjax(systemUserGroupService.insertSystemUserGroup(systemUserGroup));
    }

    /**
     * 修改用户组
     */
    @Operation(summary = "修改用户组", description = "修改用户组")
    @PreAuthorize(hasPermi = "system:userGroup:edit")
    @Log(title = "用户组", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/userGroup/edit")
    public CommonResult<Object> edit(@RequestBody SystemUserGroupDTO systemUserGroupDTO) {
        SystemUserGroup systemUserGroup = new SystemUserGroup();
        BeanUtils.copyProperties(systemUserGroupDTO, systemUserGroup);
        return toAjax(systemUserGroupService.updateSystemUserGroup(systemUserGroup));
    }

    /**
     * 删除用户组
     */
    @Operation(summary = "删除用户组", description = "删除用户组")
    @PreAuthorize(hasPermi = "system:userGroup:remove")
    @Log(title = "用户组", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/userGroup/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemUserGroupService.deleteSystemUserGroupByIds(ids));
    }

    /**
     * 查询用户组里的用户
     */
    @Operation(summary = "查询用户组里的用户", description = "查询用户组里的用户")
    @PreAuthorize(hasPermi = "system:userGroup:list")
    @PostMapping("/back/system/userGroup/user/list")
    public CommonResult<PageListDTO<Object>> listUser(@RequestBody SystemUserGroupUserFilterDTO groupUserFilterDTO) {
        startPage(groupUserFilterDTO);
        List<SystemUserGroupUserDTO> list = systemUserGroupService.selectSystemUserByGroupList(groupUserFilterDTO.getGroupId(), groupUserFilterDTO.getNickName());
        return getDataTable(list);
    }

    @Operation(summary = "查询用户组里的用户-inner", description = "查询用户组里的用户")
    @GetMapping("/inner/system/userGroup/user/{departmentId}/{groupCode}")
    public CommonResult<List<SystemUserGroupUserDTO>> selectGroupUserByDepartmentIdAndGroupCode(@PathVariable("departmentId") String departmentId,
                                                                                                @PathVariable("groupCode") String groupCode) {
        List<SystemUserGroupUserDTO> list = systemUserGroupService.selectGroupUserByDepartmentIdAndGroupCode(departmentId, groupCode);
        return CommonResult.ok(list);
    }

    /**
     * 新增用户组与用户关系
     */
    @Operation(summary = "新增用户组与用户关系", description = "新增用户组与用户关系")
    @PreAuthorize(hasPermi = "system:userGroup:add")
    @Log(title = "新增用户组与用户关系", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/userGroup/batch/save")
    public CommonResult<Object> addBatch(@RequestBody SystemUserGroupUserDTO systemUserGroupUserDTO) {
        return toAjax(systemUserGroupService.insertBatchSystemUserGroup(systemUserGroupUserDTO));
    }

}
