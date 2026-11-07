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
import com.privernal.system.domain.SystemDeptRelation;
import com.privernal.system.dto.SystemDeptRelationDTO;
import com.privernal.system.filter.SystemDeptRelationFilterDTO;
import com.privernal.system.service.ISystemDeptRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门内联关系Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 部门内联关系的接口
 */
@Tag(name = "部门内联关系相关接口")
@RestController
public class SystemDeptRelationController extends BaseController {
    @Autowired
    private ISystemDeptRelationService systemDeptRelationService;

    /**
     * 查询部门内联关系列表
     */
    @Operation(summary = "查询部门内联关系列表", description = "查询部门内联关系列表")
    @PreAuthorize(hasPermi = "system:deptRelation:list")
    @PostMapping("/back/system/deptRelation/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemDeptRelationFilterDTO systemDeptRelation) {
        startPage(systemDeptRelation);
        List<SystemDeptRelation> list = systemDeptRelationService.selectSystemDeptRelationList(systemDeptRelation);
        List<SystemDeptRelationDTO> dtoList = new ArrayList<>();
        for (SystemDeptRelation obj : list) {
            SystemDeptRelationDTO dto = new SystemDeptRelationDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取部门内联关系详细信息
     */
    @Operation(summary = "获取部门内联关系详细信息", description = "获取部门内联关系详细信息")
    @PreAuthorize(hasPermi = "system:deptRelation:query")
    @GetMapping(value = "/back/system/deptRelation/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemDeptRelationService.selectSystemDeptRelationById(id));
    }

    /**
     * 新增部门内联关系
     */
    @Operation(summary = "新增部门内联关系", description = "新增部门内联关系")
    @PreAuthorize(hasPermi = "system:deptRelation:add")
    @Log(title = "部门内联关系", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/deptRelation/save")
    public CommonResult<Object> add(@RequestBody SystemDeptRelationDTO systemDeptRelationDTO) {
        SystemDeptRelation systemDeptRelation = new SystemDeptRelation();
        BeanUtils.copyProperties(systemDeptRelationDTO, systemDeptRelation);
        return toAjax(systemDeptRelationService.insertSystemDeptRelation(systemDeptRelation));
    }

    /**
     * 修改部门内联关系
     */
    @Operation(summary = "修改部门内联关系", description = "修改部门内联关系")
    @PreAuthorize(hasPermi = "system:deptRelation:edit")
    @Log(title = "部门内联关系", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/deptRelation/edit")
    public CommonResult<Object> edit(@RequestBody SystemDeptRelationDTO systemDeptRelationDTO) {
        SystemDeptRelation systemDeptRelation = new SystemDeptRelation();
        BeanUtils.copyProperties(systemDeptRelationDTO, systemDeptRelation);
        return toAjax(systemDeptRelationService.updateSystemDeptRelation(systemDeptRelation));
    }

    /**
     * 删除部门内联关系
     */
    @Operation(summary = "删除部门内联关系", description = "删除部门内联关系")
    @PreAuthorize(hasPermi = "system:deptRelation:remove")
    @Log(title = "部门内联关系", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/deptRelation/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemDeptRelationService.deleteSystemDeptRelationByIds(ids));
    }
}
