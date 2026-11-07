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
import com.privernal.common.core.constant.MenuConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.core.utils.tree.TreeUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.annotation.ServiceRequest;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemDepartment;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.filter.SystemDepartmentFilterDTO;
import com.privernal.system.service.ISystemDepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 部门的接口
 */
@Slf4j
@Tag(name = "部门相关接口")
@RestController
public class SystemDepartmentController extends BaseController {
    @Autowired
    private ISystemDepartmentService systemDepartmentService;

    /**
     * 查询部门列表
     */
    @Operation(summary = "查询部门列表", description = "查询部门列表")
    @PreAuthorize(hasPermi = "system:department:list")
    @PostMapping("/back/system/department/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemDepartmentFilterDTO systemDepartment) {
        startPage(systemDepartment);
        systemDepartment.setStatus(WorkflowDataStatus.FINISH.getCode());
        List<SystemDepartment> list = new ArrayList<>();
        //超级管理可以查询其它组织的部门列表，在某些修改数据所属部门的场景下使用。
        if (SecurityUtils.isSuperPlatformRoleUser() && StringUtils.isNotEmpty(systemDepartment.getOrganizationId())) {
            boolean ignore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                list = systemDepartmentService.selectSystemDepartmentList(systemDepartment);
            } finally {
                OrganizationContextHolder.setIgnore(ignore);
                OrganizationContextHolder.clear();
            }
        } else {
            list = systemDepartmentService.selectSystemDepartmentList(systemDepartment);
        }

        List<SystemDepartmentDTO> dtoList = new ArrayList<>();
        for (SystemDepartment obj : list) {
            SystemDepartmentDTO dto = new SystemDepartmentDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取部门树型结构
     */
    @Operation(summary = "查询部门树型", description = "查询部门树型,不包含禁用状态的部门")
    @PreAuthorize(hasPermi = "system:department:list")
    @PostMapping("/back/system/department/tree")
    public CommonResult<?> treeDepartment(@RequestBody SystemDepartmentFilterDTO systemDepartmentFilter) {

        systemDepartmentFilter.setOrganizationId(SecurityUtils.getOrganizationId());
        systemDepartmentFilter.setStatus(WorkflowDataStatus.FINISH.getCode());
        List<SystemDepartmentDTO> list = systemDepartmentService.selectDepartmentByOrganizationId(systemDepartmentFilter);

        // 处理部门ID与组织ID相同的数据，因为这条数据，无法组织树型结构；
        // 没有 dept_relation 的历史部门 parentId 为空，按顶级部门挂到组织根下
        list = list.stream().peek(node -> {
            if (StringUtils.isEmpty(node.getParentId())) {
                node.setParentId(SecurityUtils.getOrganizationId());
            }
            if (node.getParentId().equals(node.getId())) {
                node.setParentId(MenuConstants.TOP_MENU_PARENT_ID);
            }
        }).toList();

        list = TreeUtils.convertToTree(list);

        list = list.stream().peek(node -> {
            if (MenuConstants.TOP_MENU_PARENT_ID.equals(node.getParentId())) {
                node.setParentId(node.getId());
            }
        }).toList();

        SystemDepartmentDTO top = new SystemDepartmentDTO();
        top.setChildren(list);
        top.setDeptName(SecurityUtils.getOrganizationName());
        top.setId(SecurityUtils.getOrganizationId());

        return CommonResult.ok(top);
    }

    /**
     * 获取部门树型结构
     */
    @Operation(summary = "查询部门树型", description = "查询部门树型,包含禁用的部门")
    @PreAuthorize(hasPermi = "system:department:list")
    @PostMapping("/back/system/department/all/tree")
    public CommonResult<?> treeAllDepartment(@RequestBody SystemDepartmentFilterDTO systemDepartmentFilter) {

        systemDepartmentFilter.setOrganizationId(SecurityUtils.getOrganizationId());
        List<SystemDepartmentDTO> list = systemDepartmentService.selectDepartmentByOrganizationId(systemDepartmentFilter);

        // 处理部门ID与组织ID相同的数据，因为这条数据，无法组织树型结构；
        // 没有 dept_relation 的历史部门 parentId 为空，按顶级部门挂到组织根下
        list = list.stream().peek(node -> {
            if (StringUtils.isEmpty(node.getParentId())) {
                node.setParentId(SecurityUtils.getOrganizationId());
            }
            if (node.getParentId().equals(node.getId())) {
                node.setParentId(MenuConstants.TOP_MENU_PARENT_ID);
            }
        }).toList();

        list = TreeUtils.convertToTree(list);

        list = list.stream().peek(node -> {
            if (MenuConstants.TOP_MENU_PARENT_ID.equals(node.getParentId())) {
                node.setParentId(node.getId());
            }
        }).toList();

        SystemDepartmentDTO top = new SystemDepartmentDTO();
        top.setChildren(list);
        top.setDeptName(SecurityUtils.getOrganizationName());
        top.setId(SecurityUtils.getOrganizationId());

        return CommonResult.ok(top);
    }

    /**
     * 获取部门详细信息
     */
    @Operation(summary = "获取部门详细信息", description = "获取部门详细信息")
    @PreAuthorize(hasPermi = "system:department:query")
    @GetMapping(value = "/back/system/department/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        //超级管理可以查询其它组织的部门列表，在某些修改数据所属部门的场景下使用。
        if (SecurityUtils.isSuperPlatformRoleUser()) {
            boolean ignore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                return CommonResult.ok(systemDepartmentService.selectSystemDepartmentById(id));
            } finally {
                OrganizationContextHolder.setIgnore(ignore);
                OrganizationContextHolder.clear();
            }
        } else {
            return CommonResult.ok(systemDepartmentService.selectSystemDepartmentById(id));
        }
    }

    /**
     * 新增部门
     */
    @Operation(summary = "新增部门", description = "新增部门")
    @PreAuthorize(hasPermi = "system:department:add")
    @Log(title = "部门", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/department/save")
    public CommonResult<Object> add(@RequestBody SystemDepartmentDTO systemDepartmentDTO) {
        SystemDepartment systemDepartment = new SystemDepartment();
        BeanUtils.copyProperties(systemDepartmentDTO, systemDepartment);
        systemDepartment.setParentId(systemDepartmentDTO.getParentId());
        return toAjax(systemDepartmentService.insertSystemDepartment(systemDepartment));
    }

    /**
     * 修改部门
     */
    @Operation(summary = "修改部门", description = "修改部门")
    @PreAuthorize(hasPermi = "system:department:edit")
    @Log(title = "部门", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/department/edit")
    public CommonResult<Object> edit(@RequestBody SystemDepartmentDTO systemDepartmentDTO) {
        SystemDepartment systemDepartment = new SystemDepartment();
        BeanUtils.copyProperties(systemDepartmentDTO, systemDepartment);
//        systemDepartment.setParentId(systemDepartmentDTO.getParentId());
        log.info("----------department edit  {}   {} ", systemDepartment.getParentId(), systemDepartmentDTO.getParentId());
        return toAjax(systemDepartmentService.updateSystemDepartment(systemDepartment));
    }

    /**
     * 删除部门
     */
    @Operation(summary = "删除部门", description = "删除部门")
    @PreAuthorize(hasPermi = "system:department:remove")
    @Log(title = "部门", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/department/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemDepartmentService.deleteSystemDepartmentByIds(ids));
    }

    /**
     * 查询部门列表
     */
    @ServiceRequest()
    @PostMapping("/inner/system/department/list")
    public CommonResult<Object> innerList(@RequestBody SystemDepartmentFilterDTO systemDepartment) {
        List<SystemDepartment> list = systemDepartmentService.selectDepartmentList(systemDepartment);
        return CommonResult.ok(list);
    }

}
