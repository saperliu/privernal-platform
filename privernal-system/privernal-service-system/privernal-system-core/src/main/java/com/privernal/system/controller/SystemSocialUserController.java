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
import com.privernal.system.domain.SystemOrganization;
import com.privernal.system.domain.SystemSocialUser;
import com.privernal.system.domain.SystemUser;
import com.privernal.system.dto.SystemSocialUserDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.dto.UserInfo;
import com.privernal.system.filter.SystemSocialUserFilterDTO;
import com.privernal.system.service.ISystemOrganizationService;
import com.privernal.system.service.ISystemSocialUserService;
import com.privernal.system.service.ISystemUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 社交账号Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 社交账号的接口
 */
@Tag(name = "社交账号相关接口")
@RestController
public class SystemSocialUserController extends BaseController {
    @Autowired
    private ISystemSocialUserService systemSocialUserService;

    @Autowired
    private ISystemOrganizationService systemOrganizationService;

    @Autowired
    private ISystemUserService systemUserService;

    /**
     * 查询社交账号列表
     */
    @Operation(summary = "查询社交账号列表", description = "查询社交账号列表")
    @PreAuthorize(hasPermi = "system:socialUser:list")
    @PostMapping("/back/system/socialUser/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemSocialUserFilterDTO systemSocialUser) {
        startPage(systemSocialUser);
        List<SystemSocialUser> list = systemSocialUserService.selectSystemSocialUserList(systemSocialUser);
        List<SystemSocialUserDTO> dtoList = new ArrayList<>();
        for (SystemSocialUser obj : list) {
            SystemSocialUserDTO dto = new SystemSocialUserDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取社交账号详细信息
     */
    @Operation(summary = "获取社交账号详细信息", description = "获取社交账号详细信息")
    @PreAuthorize(hasPermi = "system:socialUser:query")
    @GetMapping(value = "/back/system/socialUser/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemSocialUserService.selectSystemSocialUserById(id));
    }

    /**
     * 新增社交账号
     */
    @Operation(summary = "新增社交账号", description = "新增社交账号")
    @PreAuthorize(hasPermi = "system:socialUser:add")
    @Log(title = "社交账号", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/socialUser/save")
    public CommonResult<Object> add(@RequestBody SystemSocialUserDTO systemSocialUserDTO) {
        SystemSocialUser systemSocialUser = new SystemSocialUser();
        BeanUtils.copyProperties(systemSocialUserDTO, systemSocialUser);
        return toAjax(systemSocialUserService.insertSystemSocialUser(systemSocialUser));
    }

    /**
     * 修改社交账号
     */
    @Operation(summary = "修改社交账号", description = "修改社交账号")
    @PreAuthorize(hasPermi = "system:socialUser:edit")
    @Log(title = "社交账号", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/socialUser/edit")
    public CommonResult<Object> edit(@RequestBody SystemSocialUserDTO systemSocialUserDTO) {
        SystemSocialUser systemSocialUser = new SystemSocialUser();
        BeanUtils.copyProperties(systemSocialUserDTO, systemSocialUser);
        return toAjax(systemSocialUserService.updateSystemSocialUser(systemSocialUser));
    }

    /**
     * 删除社交账号
     */
    @Operation(summary = "删除社交账号", description = "删除社交账号")
    @PreAuthorize(hasPermi = "system:socialUser:remove")
    @Log(title = "社交账号", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/socialUser/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemSocialUserService.deleteSystemSocialUserByIds(ids));
    }

    /**
     * 新增社交账号,JustAuth方式
     */
    @ServiceRequest
    @Log(title = "社交账号", operationType = OperationType.INSERT)
    @PostMapping("/inner/social/sysSocialUser/auth/save")
    public CommonResult<UserInfo> addJustAuthSocialUser(@RequestBody SystemSocialUserDTO sysSocialUserDTO) {

        SystemUser sysUser = systemSocialUserService.saveSysSocialUser(sysSocialUserDTO);
        return findSocialUser(sysUser, sysSocialUserDTO);
    }

    /**
     * 新增社交账号
     */
    @ServiceRequest
    @Log(title = "社交账号自动登录", operationType = OperationType.UPDATE)
    @PostMapping("/inner/social/sysSocialUser/find")
    public CommonResult<UserInfo> findSocialUser(@RequestBody SystemSocialUserDTO systemSocialUserDTO) {

        SystemUser sysUser = systemSocialUserService.findSocialUser(systemSocialUserDTO);
        if (sysUser == null) {
            return CommonResult.fail("未找到此用户");
        } else {
            return findSocialUser(sysUser, systemSocialUserDTO);
        }
    }

    /**
     * 登录成功后，返回用户信息，放在token里
     *
     * @param sysUser          用户
     * @param sysSocialUserDTO 社交用户参数
     * @return 用户信息
     */
    private CommonResult<UserInfo> findSocialUser(SystemUser sysUser, SystemSocialUserDTO sysSocialUserDTO) {

        SystemUserDTO userDTO = new SystemUserDTO();
        org.springframework.beans.BeanUtils.copyProperties(sysUser, userDTO);
        UserInfo userInfo = systemUserService.getUserInfo(userDTO, sysUser.getOrganizationId());

        SystemOrganization systemOrganization = systemOrganizationService.selectSystemOrganizationById(sysUser.getOrganizationId());
        userInfo.setOrganizationName(systemOrganization.getOrganizationName());
        userInfo.setOpenId(sysSocialUserDTO.getOpenId());

        return CommonResult.ok(userInfo);
    }

}
