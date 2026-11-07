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


import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.annotation.ServiceRequest;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.domain.SystemUser;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.filter.WorkflowCodeFilterDTO;
import com.privernal.system.filter.WorkflowUserFilterDTO;
import com.privernal.system.service.ISystemDepartmentService;
import com.privernal.system.service.ISystemOrganizationService;
import com.privernal.system.service.ISystemUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2023-02-12 14:12
 * @Description WorkflowUserController 工作流内部使用的查询人的接口
 */
@Tag(name = "内部工作流查询相关接口")
@RestController
public class WorkflowUserController {

    @Autowired
    private ISystemUserService systemUserService;

    @Autowired
    private ISystemDepartmentService systemDepartmentService;

    @Autowired
    private ISystemOrganizationService systemOrganizationService;

    /**
     * 获取用户信息
     * 工作流批量查询用户信息
     */
    @ServiceRequest()
    @GetMapping("/inner/user/find/id")
    @ResponseBody
    public CommonResult<SystemUserDTO> findUserListByIds(@RequestParam("userId") String userId) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            SystemUser systemUser = systemUserService.selectSystemUserById(userId);
            SystemUserDTO systemUserDTO = new SystemUserDTO();
            BeanUtils.copyProperties(systemUser, systemUserDTO);
            return CommonResult.ok(systemUserDTO);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }


    /**
     * 获取用户信息
     * 工作流批量查询用户信息
     */
    @ServiceRequest()
    @PostMapping("/inner/user/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserListByIds(@RequestBody WorkflowUserFilterDTO filterDTO) {
        if (StringUtils.isEmpty(filterDTO.getUserList())) {
            return CommonResult.fail(new ArrayList<>(), "用户ID集合不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByIdList(filterDTO.getUserList());
            return CommonResult.ok(systemUserList);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

    }


    /**
     * 获取用户信息
     * 工作流批量查询用户信息,根据部门
     */
    @ServiceRequest()
    @PostMapping("/inner/user/dept/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByDept(@RequestBody WorkflowUserFilterDTO filterDTO) {
        if (StringUtils.isEmpty(filterDTO.getDeptList())) {
            return CommonResult.fail(new ArrayList<>(), "部门ID集合不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByDeptIdList(filterDTO.getDeptList());

            return CommonResult.ok(systemUserList);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

    }


    /**
     * 获取用户信息
     * 工作流批量查询用户信息,根据角色
     */
    @ServiceRequest()
    @PostMapping("/inner/user/role/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByRole(@RequestBody WorkflowUserFilterDTO filterDTO) {

        if (StringUtils.isEmpty(filterDTO.getRoleList())) {
            return CommonResult.fail(new ArrayList<>(), "角色ID集合不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByRoleIdList(filterDTO.getRoleList(), filterDTO.getDepartmentId());
            return CommonResult.ok(systemUserList);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

    }


    /**
     * 获取用户信息
     * 工作流批量查询用户信息,根据用户组
     */
    @ServiceRequest()
    @PostMapping("/inner/user/group/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByGroup(@RequestBody WorkflowUserFilterDTO filterDTO) {
        if (StringUtils.isEmpty(filterDTO.getUserGroupList())) {
            return CommonResult.fail(new ArrayList<>(), "用户组ID集合不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByGroupIdList(filterDTO.getUserGroupList(), filterDTO.getDepartmentId());
            return CommonResult.ok(systemUserList);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

    }

    /**
     * 获取部门信息
     * 工作流批量查询部门信息
     */
    @ServiceRequest()
    @PostMapping("/inner/department/find/list")
    @ResponseBody
    public CommonResult<List<SystemDepartmentDTO>> findDeptListByIds(@RequestBody WorkflowUserFilterDTO filterDTO) {
        if (StringUtils.isEmpty(filterDTO.getDeptList())) {
            return CommonResult.fail(new ArrayList<>(), "部门ID集合不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemDepartmentDTO> systemDepartmentList = systemDepartmentService.selectSystemDepartmentList(filterDTO.getDeptList());
            return CommonResult.ok(systemDepartmentList);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

    }

    /**
     * 获取部门详细信息
     */
    @ServiceRequest()
    @GetMapping(value = "/inner/system/department/{id}")
    public CommonResult<Object> findDeptById(@PathVariable("id") String id) {

        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            return CommonResult.ok(systemDepartmentService.selectSystemDepartmentById(id));
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

    }

    /**
     * 获取组织详细信息
     */
    @Operation(summary = "获取组织详细信息", description = "获取组织详细信息")
    @ServiceRequest()
    @GetMapping(value = "/inner/system/organization/{id}")
    public CommonResult<Object> findOrganizationById(@PathVariable("id") String id) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            return CommonResult.ok(systemOrganizationService.selectSystemOrganizationById(id));
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

    }


    /**
     * 获取用户信息
     * 工作流批量查询用户信息,根据角色编码
     */
    @ServiceRequest()
    @PostMapping("/inner/user/roleCode/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByRoleCode(@RequestBody WorkflowCodeFilterDTO filterDTO) {

        if (StringUtils.isNotEmpty(filterDTO.getCode())) {
            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);

                List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByRoleCodeList(filterDTO.getCode(), filterDTO.getDepartmentId(), filterDTO.getOrganizationId());
                return CommonResult.ok(systemUserList);
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }

        } else {
            if (StringUtils.isEmpty(filterDTO.getCodeList())) {
                return CommonResult.fail(new ArrayList<>(), "角色编码集合不能为空");
            }
            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                return CommonResult.ok(systemUserService.selectUserDTOByRoleCodes(filterDTO.getCodeList(), filterDTO.getDepartmentId(), filterDTO.getOrganizationId()));
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }
        }
    }


    /**
     * 获取用户信息
     * 工作流批量查询用户信息,根据用户组编码
     */
    @ServiceRequest()
    @PostMapping("/inner/user/groupCode/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByGroupCode(@RequestBody WorkflowCodeFilterDTO filterDTO) {
        if (StringUtils.isNotEmpty(filterDTO.getCode())) {

            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByGroupCodeList(filterDTO.getCode(), filterDTO.getDepartmentId(), filterDTO.getOrganizationId());
                return CommonResult.ok(systemUserList);
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }

        } else {
            if (StringUtils.isEmpty(filterDTO.getCodeList())) {
                return CommonResult.fail(new ArrayList<>(), "用户组编码集合不能为空");
            }
            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                return CommonResult.ok(systemUserService.selectUserDTOByGroupCodes(filterDTO.getCodeList(), filterDTO.getDepartmentId(), filterDTO.getOrganizationId()));
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }
        }
    }

}
