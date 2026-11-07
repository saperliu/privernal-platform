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

import com.privernal.common.core.constant.UserConstants;
import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.annotation.ServiceRequest;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.security.service.TokenService;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemUser;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.dto.UserInfo;
import com.privernal.system.filter.SystemUserFilterDTO;
import com.privernal.system.service.ISystemUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户的接口
 */
@Tag(name = "用户相关接口")
@RestController
public class SystemUserController extends BaseController {
    @Autowired
    private ISystemUserService systemUserService;

    @Autowired
    private TokenService tokenService;

    /**
     * 查询用户列表
     */
    @Operation(summary = "查询用户列表", description = "查询用户列表")
    @PreAuthorize(hasPermi = "system:user:list")
    @PostMapping("/back/system/user/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemUserFilterDTO systemUser) {
        startPage(systemUser);
        // 排除个人
//        systemUser.setUserId(SecurityUtils.getUserId());
        systemUser.setStatus(WorkflowDataStatus.FINISH.getCode());
        List<SystemUser> list = systemUserService.selectSystemUserList(systemUser);
        List<SystemUserDTO> dtoList = new ArrayList<>();
        for (SystemUser obj : list) {
            SystemUserDTO dto = new SystemUserDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dto.setPassword(null);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 查询用户列表
     */
    @Operation(summary = "查询用户列表", description = "查询用户列表")
    @PreAuthorize(hasPermi = "system:user:list")
    @PostMapping("/back/system/user/all/list")
    public CommonResult<PageListDTO<Object>> allList(@RequestBody SystemUserFilterDTO systemUser) {
        startPage(systemUser);
        // 排除个人
//        systemUser.setUserId(SecurityUtils.getUserId());
        List<SystemUser> list = systemUserService.selectSystemUserList(systemUser);
        List<SystemUserDTO> dtoList = new ArrayList<>();
        for (SystemUser obj : list) {
            SystemUserDTO dto = new SystemUserDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dto.setPassword(null);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取用户详细信息
     */
    @Operation(summary = "获取用户详细信息", description = "获取用户详细信息")
    @PreAuthorize(hasPermi = "system:user:query")
    @GetMapping(value = "/back/system/user/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        SystemUser systemUser = systemUserService.selectSystemUserById(id);
        if (StringUtils.isNotNull(systemUser)) {
            SystemUserDTO systemUserDTO = new SystemUserDTO();
            BeanUtils.copyProperties(systemUser, systemUserDTO, DataConstants.IGNORED_ON_COPY);
            systemUserDTO.setPassword(null);
            return CommonResult.ok(systemUserDTO);
        }
        return CommonResult.fail("无此用户");
    }

    /**
     * 获取用户详细信息，在回显用户时使用，去掉了查询权限
     */
    @Operation(summary = "获取用户详细信息", description = "获取用户详细信息")
    @PreAuthorize(hasPermi = "system:user:query")
    @GetMapping(value = "/back/system/user/org/{id}/{organizationId}")
    public CommonResult<Object> getInfoByOrgId(@PathVariable("organizationId") String organizationId, @PathVariable("id") String id) {
        Boolean oldIgnored = OrganizationContextHolder.isIgnore();
        try {
            if (StringUtils.isNotEmpty(organizationId)) {
                OrganizationContextHolder.setOrganizationId(organizationId);
            }
            SystemUser systemUser = systemUserService.selectSystemUserById(id);
            if (StringUtils.isNotNull(systemUser)) {
                SystemUserDTO systemUserDTO = new SystemUserDTO();
                BeanUtils.copyProperties(systemUser, systemUserDTO, DataConstants.IGNORED_ON_COPY);
                systemUserDTO.setPassword(null);
                systemUserDTO.setEmail(null);
                systemUserDTO.setPhone(null);
                systemUserDTO.setIdentityNum(null);
                systemUserDTO.setUserType(null);
                return CommonResult.ok(systemUserDTO);
            }
        }finally {
            OrganizationContextHolder.setIgnore(oldIgnored);
            OrganizationContextHolder.clear();
        }
        return CommonResult.fail("无此用户");
    }

    /**
     * 新增用户
     */
    @Operation(summary = "新增用户", description = "新增用户")
    @PreAuthorize(hasPermi = "system:user:add")
    @Log(title = "用户", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/user/save")
    public CommonResult<Object> add(@RequestBody SystemUserDTO systemUserDTO) {

        SystemUser systemUser = new SystemUser();
        BeanUtils.copyProperties(systemUserDTO, systemUser);
        systemUser.setDataScope(systemUserDTO.getDataScope());

        Integer result = systemUserService.insertSystemUser(systemUser);

        if (UserConstants.NOT_UNIQUE_LOGIN_NAME.equals(result)) {
            return CommonResult.fail("新增用户'" + systemUserDTO.getLoginName() + "'失败，登录账号已存在");
        } else if (StringUtils.isNotEmpty(systemUserDTO.getPhone())
                && UserConstants.NOT_UNIQUE_MOBILE.equals(result)) {
            return CommonResult.fail("新增用户'" + systemUserDTO.getLoginName() + "'失败，手机号码已存在");
        } else if (StringUtils.isNotEmpty(systemUserDTO.getEmail())
                && UserConstants.NOT_UNIQUE_EMAIL.equals(result)) {
            return CommonResult.fail("新增用户'" + systemUserDTO.getLoginName() + "'失败，邮箱账号已存在");
        } else if (UserConstants.DATA_SCOPE_MAX.equals(result)) {
            return CommonResult.fail("新增用户'" + systemUserDTO.getLoginName() + "'失败，授权权限过大");
        }
        return toAjax(result);
    }

    /**
     * 修改用户
     */
    @Operation(summary = "修改用户", description = "修改用户")
    @PreAuthorize(hasPermi = "system:user:edit")
    @Log(title = "用户", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/user/edit")
    public CommonResult<Object> edit(@RequestBody SystemUserDTO systemUserDTO) {
        SystemUser systemUser = new SystemUser();
        BeanUtils.copyProperties(systemUserDTO, systemUser);

        Integer result = systemUserService.updateSystemUser(systemUser);

        if (UserConstants.NOT_UNIQUE_LOGIN_NAME.equals(result)) {
            return CommonResult.fail("修改用户'" + systemUserDTO.getLoginName() + "'失败，登录账号已存在");
        } else if (StringUtils.isNotEmpty(systemUserDTO.getPhone())
                && UserConstants.NOT_UNIQUE_MOBILE.equals(result)) {
            return CommonResult.fail("修改用户'" + systemUserDTO.getLoginName() + "'失败，手机号码已存在");
        } else if (StringUtils.isNotEmpty(systemUserDTO.getEmail())
                && UserConstants.NOT_UNIQUE_EMAIL.equals(result)) {
            return CommonResult.fail("修改用户'" + systemUserDTO.getLoginName() + "'失败，邮箱账号已存在");
        }
        return toAjax(result);
    }

    /**
     * 删除用户
     */
    @Operation(summary = "删除用户", description = "删除用户")
    @PreAuthorize(hasPermi = "system:user:remove")
    @Log(title = "用户", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/user/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemUserService.deleteSystemUserByIds(ids));
    }

    /**
     * 获取用户详细信息，在回显用户时使用，去掉了查询权限
     */
    @Operation(summary = "获取用户数量", description = "获取用户数量")
    @PreAuthorize(hasPermi = "system:user:query")
    @GetMapping(value = "/back/system/user/num")
    public CommonResult<Object> getOrgUserNum() {
        String id = SecurityUtils.getOrganizationId();
        return CommonResult.ok(systemUserService.selectCountUserByOrganizationId(id));
    }

    /**
     * 登录时获取当前用户信息，验证模块负责验证用户
     */
    @ServiceRequest()
    @GetMapping("/inner/user/info/{username}")
    @ResponseBody
    public CommonResult<UserInfo> info(@PathVariable("username") String username) {
        //登录使用，此时未登录，去掉组织的限制
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            SystemUserDTO sysUser = systemUserService.selectUserByPhone(username);
            if (StringUtils.isNull(sysUser)) {
                // 用户名没有查询到，查询是否是手机号登录
                sysUser = systemUserService.selectUserByLoginName(username);
                if (StringUtils.isNull(sysUser)) {
                    return CommonResult.fail("用户名或密码错误");
                }
            }
            if (DataConstants.DATA_DELETE_FLAG_YES.equals(sysUser.getDeletedFlag())) {
                return CommonResult.fail("用户已被删除");
            }
            if (WorkflowDataStatus.CANCEL.getCode().equals(sysUser.getStatus())) {
                return CommonResult.fail("用户已被禁用");
            }
            UserInfo userInfo = new UserInfo();
            org.springframework.beans.BeanUtils.copyProperties(sysUser, userInfo);
            userInfo.setUserId(sysUser.getId());
            userInfo.setNickName(sysUser.getNickName());
            userInfo.setLoginName(sysUser.getLoginName());
            userInfo.setLoginTime(System.currentTimeMillis());
            userInfo.setOrganizationId(sysUser.getOrganizationId());
            userInfo.setOrganizationName(sysUser.getOrganizationName());
            userInfo.setPassword(sysUser.getPassword());
            // 查询用户拥有的组织, 设置切换后这个组织的默认部门,查询时已经查出
            userInfo.setDepartmentId(sysUser.getDepartmentId());
            return CommonResult.ok(userInfo);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    /**
     * 获取当前用户信息
     */
    @ServiceRequest()
    @GetMapping("/inner/user/change/{organizationId}/{userId}")
    @ResponseBody
    public CommonResult<UserInfo> changeOrganization(@PathVariable("organizationId") String organizationId, @PathVariable("userId") String userId) {
        Assert.hasLength(organizationId, "组织ID不能为空");
        // S3(p5①)：有登录态时只允许切当前用户——该端点会把目标用户信息写入"调用方会话"，
        // 不加限制等于凭内部头即可装载任意用户身份
        String loginUserId = SecurityUtils.getUserId();
        if (StringUtils.isNotEmpty(loginUserId) && !loginUserId.equals(userId)) {
            throw new ServiceException("只能切换当前登录用户的组织");
        }
        SystemUserDTO sysUser = systemUserService.selectUserDTOByOrgIdAndId(userId, organizationId);
        Assert.notNull(sysUser, "切换失败");
        sysUser.setPassword(null);
        UserInfo userInfo = systemUserService.getUserInfo(sysUser, organizationId);
        // 设置信息缓存
        tokenService.setLoginUser(userInfo);
        return CommonResult.ok(userInfo);
    }

    /**
     * 登录后获取用户信息，返回给前台的用户信息
     *
     * @return 用户信息
     */
    @Operation(summary = "获取用户信息", description = "登录成功后,先查询用户信息")
    @GetMapping("/back/system/user/getInfo")
    public CommonResult<?> getInfo() {
        String userId = SecurityUtils.getUserId();
        String organizationId = SecurityUtils.getOrganizationId();
        SystemUserDTO sysUser = systemUserService.selectUserDTOByOrgIdAndId(userId, organizationId);
        UserInfo userInfo = systemUserService.getUserInfo(sysUser, organizationId);
        userInfo.setPassword("");
        // 设置信息缓存
        tokenService.setLoginUser(userInfo);
        return CommonResult.ok(userInfo);
    }

    /**
     * 删除当前用户信息
     */
    @ServiceRequest()
    @DeleteMapping("/inner/user/delete/{id}")
    @ResponseBody
    public CommonResult<Object> deleteUser(@PathVariable("id") String id) {
        // S3(p5①)：有登录态时只允许注销/删除当前登录用户
        String loginUserId = SecurityUtils.getUserId();
        if (StringUtils.isNotEmpty(loginUserId) && !loginUserId.equals(id)) {
            throw new ServiceException("只能删除当前登录用户");
        }
        if (StringUtils.isNotEmpty(id)) {
            int i = systemUserService.deleteSystemUserById(id);
            return CommonResult.ok(i);
        }
        return CommonResult.fail("修改个人信息异常，请联系管理员");
    }
}
