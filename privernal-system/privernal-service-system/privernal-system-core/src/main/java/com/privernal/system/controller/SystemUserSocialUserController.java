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
import com.privernal.system.domain.SystemUserSocialUser;
import com.privernal.system.dto.SystemUserSocialUserDTO;
import com.privernal.system.filter.SystemUserSocialUserFilterDTO;
import com.privernal.system.service.ISystemUserSocialUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 社交帐号关联系统账号Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 社交帐号关联系统账号的接口
 */
@Tag(name = "社交帐号关联系统账号相关接口")
@RestController
public class SystemUserSocialUserController extends BaseController {
    @Autowired
    private ISystemUserSocialUserService systemUserSocialUserService;

    /**
     * 查询社交帐号关联系统账号列表
     */
    @Operation(summary = "查询社交帐号关联系统账号列表", description = "查询社交帐号关联系统账号列表")
    @PreAuthorize(hasPermi = "system:userSocialUser:list")
    @PostMapping("/back/system/userSocialUser/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemUserSocialUserFilterDTO systemUserSocialUser) {
        startPage(systemUserSocialUser);
        List<SystemUserSocialUser> list = systemUserSocialUserService.selectSystemUserSocialUserList(systemUserSocialUser);
        List<SystemUserSocialUserDTO> dtoList = new ArrayList<>();
        for (SystemUserSocialUser obj : list) {
            SystemUserSocialUserDTO dto = new SystemUserSocialUserDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取社交帐号关联系统账号详细信息
     */
    @Operation(summary = "获取社交帐号关联系统账号详细信息", description = "获取社交帐号关联系统账号详细信息")
    @PreAuthorize(hasPermi = "system:userSocialUser:query")
    @GetMapping(value = "/back/system/userSocialUser/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemUserSocialUserService.selectSystemUserSocialUserById(id));
    }

    /**
     * 新增社交帐号关联系统账号
     */
    @Operation(summary = "新增社交帐号关联系统账号", description = "新增社交帐号关联系统账号")
    @PreAuthorize(hasPermi = "system:userSocialUser:add")
    @Log(title = "社交帐号关联系统账号", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/userSocialUser/save")
    public CommonResult<Object> add(@RequestBody SystemUserSocialUserDTO systemUserSocialUserDTO) {
        SystemUserSocialUser systemUserSocialUser = new SystemUserSocialUser();
        BeanUtils.copyProperties(systemUserSocialUserDTO, systemUserSocialUser);
        return toAjax(systemUserSocialUserService.insertSystemUserSocialUser(systemUserSocialUser));
    }

    /**
     * 修改社交帐号关联系统账号
     */
    @Operation(summary = "修改社交帐号关联系统账号", description = "修改社交帐号关联系统账号")
    @PreAuthorize(hasPermi = "system:userSocialUser:edit")
    @Log(title = "社交帐号关联系统账号", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/userSocialUser/edit")
    public CommonResult<Object> edit(@RequestBody SystemUserSocialUserDTO systemUserSocialUserDTO) {
        SystemUserSocialUser systemUserSocialUser = new SystemUserSocialUser();
        BeanUtils.copyProperties(systemUserSocialUserDTO, systemUserSocialUser);
        return toAjax(systemUserSocialUserService.updateSystemUserSocialUser(systemUserSocialUser));
    }

    /**
     * 删除社交帐号关联系统账号
     */
    @Operation(summary = "删除社交帐号关联系统账号", description = "删除社交帐号关联系统账号")
    @PreAuthorize(hasPermi = "system:userSocialUser:remove")
    @Log(title = "社交帐号关联系统账号", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/userSocialUser/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemUserSocialUserService.deleteSystemUserSocialUserByIds(ids));
    }
}
