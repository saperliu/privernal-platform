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
import com.privernal.system.domain.SystemOrganizationMenu;
import com.privernal.system.dto.SystemOrganizationMenuCreateDTO;
import com.privernal.system.dto.SystemOrganizationMenuDTO;
import com.privernal.system.filter.SystemOrganizationMenuFilterDTO;
import com.privernal.system.service.ISystemOrganizationMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织拥有的菜单Controller
 *
 * @author saperliu
 * @date 2023-04-17
 * @Description 组织拥有的菜单的接口
 */
@Tag(name = "组织拥有的菜单相关接口")
@RestController
public class SystemOrganizationMenuController extends BaseController {
    @Autowired
    private ISystemOrganizationMenuService systemOrganizationMenuService;

    /**
     * 查询组织拥有的菜单列表
     */
    @Operation(summary = "查询组织拥有的菜单列表", description = "查询组织拥有的菜单列表")
    @PreAuthorize(hasPermi = "system:organizationMenu:list")
    @PostMapping("/back/system/organizationMenu/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemOrganizationMenuFilterDTO systemOrganizationMenu) {
        startPage(systemOrganizationMenu);
        List<SystemOrganizationMenuDTO> list = systemOrganizationMenuService.selectSystemOrganizationMenuList(systemOrganizationMenu);
        List<SystemOrganizationMenuDTO> dtoList = new ArrayList<>();
        for (SystemOrganizationMenuDTO obj : list) {
            SystemOrganizationMenuDTO dto = new SystemOrganizationMenuDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        return getDataTable(dtoList);
    }

    /**
     * 获取组织拥有的菜单详细信息
     */
    @Operation(summary = "获取组织拥有的菜单详细信息", description = "获取组织拥有的菜单详细信息")
    @PreAuthorize(hasPermi = "system:organizationMenu:query")
    @GetMapping(value = "/back/system/organizationMenu/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemOrganizationMenuService.selectSystemOrganizationMenuById(id));
    }

    /**
     * 新增组织拥有的菜单
     */
    @Operation(summary = "新增组织拥有的菜单", description = "新增组织拥有的菜单")
    @PreAuthorize(hasPermi = "system:organizationMenu:add")
    @Log(title = "组织拥有的菜单", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/organizationMenu/save")
    public CommonResult<Object> add(@RequestBody SystemOrganizationMenuCreateDTO systemOrganizationMenuCreateDTO) {
        SystemOrganizationMenu systemOrganizationMenu = new SystemOrganizationMenu();
        BeanUtils.copyProperties(systemOrganizationMenuCreateDTO, systemOrganizationMenu);
        return toAjax(systemOrganizationMenuService.insertSystemOrganizationMenu(systemOrganizationMenu));
    }

    /**
     * 修改组织拥有的菜单
     */
    @Operation(summary = "修改组织拥有的菜单", description = "修改组织拥有的菜单")
    @PreAuthorize(hasPermi = "system:organizationMenu:edit")
    @Log(title = "组织拥有的菜单", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/organizationMenu/edit")
    public CommonResult<Object> edit(@RequestBody SystemOrganizationMenuCreateDTO systemOrganizationMenuCreateDTO) {
        SystemOrganizationMenu systemOrganizationMenu = new SystemOrganizationMenu();
        BeanUtils.copyProperties(systemOrganizationMenuCreateDTO, systemOrganizationMenu);
        return toAjax(systemOrganizationMenuService.updateSystemOrganizationMenu(systemOrganizationMenu));
    }

    /**
     * 删除组织拥有的菜单
     */
    @Operation(summary = "删除组织拥有的菜单", description = "删除组织拥有的菜单")
    @PreAuthorize(hasPermi = "system:organizationMenu:remove")
    @Log(title = "组织拥有的菜单", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/organizationMenu/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemOrganizationMenuService.deleteSystemOrganizationMenuByIds(ids));
    }
}
