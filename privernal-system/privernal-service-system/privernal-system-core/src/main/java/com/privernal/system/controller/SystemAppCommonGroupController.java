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
import com.privernal.system.domain.SystemAppCommonGroup;
import com.privernal.system.dto.SystemAppCommonGroupDTO;
import com.privernal.system.filter.SystemAppCommonGroupFilterDTO;
import com.privernal.system.service.ISystemAppCommonGroupService;
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
 * 应用关联的公共组Controller
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用关联的公共组的接口
 */
@Tag(name = "应用关联的公共组相关接口")
@RestController
@Slf4j
public class SystemAppCommonGroupController extends BaseController {
    @Autowired
    private ISystemAppCommonGroupService systemAppCommonGroupService;

    /**
     * 查询应用关联的公共组列表
     */
    @Operation(summary = "查询应用关联的公共组列表", description = "查询应用关联的公共组列表")
    @PreAuthorize(hasPermi = "system:appCommonGroup:list")
    @PostMapping("/back/system/appCommonGroup/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemAppCommonGroupFilterDTO systemAppCommonGroup) {
        startPage(systemAppCommonGroup);
        List<SystemAppCommonGroupDTO> list = systemAppCommonGroupService.selectSystemAppCommonGroupList(systemAppCommonGroup);
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        return tableResult;
    }

    /**
     * 获取应用关联的公共组详细信息
     */
    @Operation(summary = "获取应用关联的公共组详细信息", description = "获取应用关联的公共组详细信息")
    @PreAuthorize(hasPermi = "system:appCommonGroup:query")
    @GetMapping(value = "/back/system/appCommonGroup/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemAppCommonGroupService.selectSystemAppCommonGroupDetailDTOById(id));
    }

    /**
     * 新增应用关联的公共组
     */
    @Operation(summary = "新增应用关联的公共组", description = "新增应用关联的公共组")
    @PreAuthorize(hasPermi = "system:appCommonGroup:add")
    @Log(title = "应用关联的公共组", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/appCommonGroup/save")
    public CommonResult<Object> add(@RequestBody SystemAppCommonGroupDTO systemAppCommonGroupDTO) {
        SystemAppCommonGroup systemAppCommonGroup = new SystemAppCommonGroup();
        BeanUtils.copyProperties(systemAppCommonGroupDTO, systemAppCommonGroup);
        return CommonResult.ok(systemAppCommonGroupService.insertSystemAppCommonGroup(systemAppCommonGroup));
    }

    /**
     * 修改应用关联的公共组
     */
    @Operation(summary = "修改应用关联的公共组", description = "修改应用关联的公共组")
    @PreAuthorize(hasPermi = "system:appCommonGroup:edit")
    @Log(title = "应用关联的公共组", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/appCommonGroup/edit")
    public CommonResult<Object> edit(@RequestBody SystemAppCommonGroupDTO systemAppCommonGroupDTO) {
        SystemAppCommonGroup systemAppCommonGroup = new SystemAppCommonGroup();
        BeanUtils.copyProperties(systemAppCommonGroupDTO, systemAppCommonGroup);
        return CommonResult.ok(systemAppCommonGroupService.updateSystemAppCommonGroup(systemAppCommonGroup));
    }

    /**
     * 删除应用关联的公共组
     */
    @Operation(summary = "删除应用关联的公共组", description = "删除应用关联的公共组")
    @PreAuthorize(hasPermi = "system:appCommonGroup:remove")
    @Log(title = "应用关联的公共组", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/appCommonGroup/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemAppCommonGroupService.deleteSystemAppCommonGroupByIds(ids));
    }
}
