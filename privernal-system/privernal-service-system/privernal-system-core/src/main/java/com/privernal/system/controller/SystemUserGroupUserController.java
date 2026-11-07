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
import com.privernal.system.domain.SystemUserGroupUser;
import com.privernal.system.dto.SystemUserGroupUserDTO;
import com.privernal.system.filter.SystemUserGroupUserFilterDTO;
import com.privernal.system.service.ISystemUserGroupUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户组与用户Controller
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户组与用户的接口
 */
@Tag(name = "用户组与用户相关接口")
@RestController
public class SystemUserGroupUserController extends BaseController {
    @Autowired
    private ISystemUserGroupUserService systemUserGroupUserService;

    /**
     * 查询用户组与用户列表
     */
    @Operation(summary = "查询用户组与用户列表", description = "查询用户组与用户列表")
    @PreAuthorize(hasPermi = "system:userGroupUser:list")
    @PostMapping("/back/system/userGroupUser/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemUserGroupUserFilterDTO systemUserGroupUser) {
        startPage(systemUserGroupUser);
        List<SystemUserGroupUser> list = systemUserGroupUserService.selectSystemUserGroupUserList(systemUserGroupUser);
        List<SystemUserGroupUserDTO> dtoList = new ArrayList<>();
        for (SystemUserGroupUser obj : list) {
            SystemUserGroupUserDTO dto = new SystemUserGroupUserDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取用户组与用户详细信息
     */
    @Operation(summary = "获取用户组与用户详细信息", description = "获取用户组与用户详细信息")
    @PreAuthorize(hasPermi = "system:userGroupUser:query")
    @GetMapping(value = "/back/system/userGroupUser/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemUserGroupUserService.selectSystemUserGroupUserById(id));
    }

    /**
     * 新增用户组与用户
     */
    @Operation(summary = "新增用户组与用户", description = "新增用户组与用户")
    @PreAuthorize(hasPermi = "system:userGroupUser:add")
    @Log(title = "用户组与用户", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/userGroupUser/save")
    public CommonResult<Object> add(@RequestBody SystemUserGroupUserDTO systemUserGroupUserDTO) {
        SystemUserGroupUser systemUserGroupUser = new SystemUserGroupUser();
        BeanUtils.copyProperties(systemUserGroupUserDTO, systemUserGroupUser);
        return toAjax(systemUserGroupUserService.insertSystemUserGroupUser(systemUserGroupUser));
    }

    /**
     * 修改用户组与用户
     */
    @Operation(summary = "修改用户组与用户", description = "修改用户组与用户")
    @PreAuthorize(hasPermi = "system:userGroupUser:edit")
    @Log(title = "用户组与用户", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/userGroupUser/edit")
    public CommonResult<Object> edit(@RequestBody SystemUserGroupUserDTO systemUserGroupUserDTO) {
        SystemUserGroupUser systemUserGroupUser = new SystemUserGroupUser();
        BeanUtils.copyProperties(systemUserGroupUserDTO, systemUserGroupUser);
        return toAjax(systemUserGroupUserService.updateSystemUserGroupUser(systemUserGroupUser));
    }

    /**
     * 删除用户组与用户
     */
    @Operation(summary = "删除用户组与用户", description = "删除用户组与用户")
    @PreAuthorize(hasPermi = "system:userGroupUser:remove")
    @Log(title = "用户组与用户", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/userGroupUser/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemUserGroupUserService.deleteSystemUserGroupUserByIds(ids));
    }
}
