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
import com.privernal.common.core.utils.tree.TreeNodeConfig;
import com.privernal.common.core.utils.tree.TreeUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemCommonGroupMenu;
import com.privernal.system.dto.SystemCommonGroupMenuDTO;
import com.privernal.system.filter.SystemCommonGroupMenuFilterDTO;
import com.privernal.system.service.ISystemCommonGroupMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 组关联的权限Controller
 *
 * @author saperliu
 * @date 2024-07-10
 * @Description 组关联的权限的接口
 */
@Tag(name = "组关联的权限相关接口")
@RestController
@Slf4j
public class SystemCommonGroupMenuController extends BaseController {
    @Autowired
    private ISystemCommonGroupMenuService systemCommonGroupMenuService;

    /**
     * 查询组关联的权限列表
     */
    @Operation(summary = "查询组关联的权限列表", description = "查询组关联的权限列表")
    @PreAuthorize(hasPermi = "system:commonGroupMenu:list")
    @PostMapping("/back/system/commonGroupMenu/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemCommonGroupMenuFilterDTO systemCommonGroupMenu) {
        startPage(systemCommonGroupMenu);
        List<SystemCommonGroupMenuDTO> list = systemCommonGroupMenuService.selectSystemCommonGroupMenuList(systemCommonGroupMenu);
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        return tableResult;
    }

    /**
     * 获取菜单树型结构,应用的子表内容
     * 显示应用关联的菜单时。
     */
    @Operation(summary = "查询所有菜单组织成树型", description = "查询所有关联的权限组织成树型")
    @PreAuthorize(hasPermi = "system:commonGroup:list")
    @PostMapping("/back/system/commonGroupMenu/tree")
    public CommonResult<?> treeAllMenu(@RequestBody SystemCommonGroupMenuFilterDTO systemCommonGroupMenu) {
        List<SystemCommonGroupMenuDTO> menus = systemCommonGroupMenuService.selectCommGroupMenuListByCommonGroupId(systemCommonGroupMenu);
        TreeNodeConfig treeNodeConfig = new TreeNodeConfig();
        //// 自定义属性名 都要默认值的
        treeNodeConfig.setIdKey("menuId");
        menus = TreeUtils.convertToTree(menus, treeNodeConfig, null);
        return CommonResult.ok(menus);
    }

    /**
     * 获取组关联的权限详细信息
     */
    @Operation(summary = "获取组关联的权限详细信息", description = "获取组关联的权限详细信息")
    @PreAuthorize(hasPermi = "system:commonGroupMenu:query")
    @GetMapping(value = "/back/system/commonGroupMenu/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemCommonGroupMenuService.selectSystemCommonGroupMenuDetailDTOById(id));
    }

    /**
     * 新增组关联的权限
     */
    @Operation(summary = "新增组关联的权限", description = "新增组关联的权限")
    @PreAuthorize(hasPermi = "system:commonGroupMenu:add")
    @Log(title = "组关联的权限", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/commonGroupMenu/save")
    public CommonResult<Object> add(@RequestBody SystemCommonGroupMenuDTO systemCommonGroupMenuDTO) {
        SystemCommonGroupMenu systemCommonGroupMenu = new SystemCommonGroupMenu();
        BeanUtils.copyProperties(systemCommonGroupMenuDTO, systemCommonGroupMenu, DataConstants.IGNORED_ON_COPY);
        return CommonResult.ok(systemCommonGroupMenuService.insertSystemCommonGroupMenu(systemCommonGroupMenu));
    }

    /**
     * 修改组关联的权限
     */
    @Operation(summary = "修改组关联的权限", description = "修改组关联的权限")
    @PreAuthorize(hasPermi = "system:commonGroupMenu:edit")
    @Log(title = "组关联的权限", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/commonGroupMenu/edit")
    public CommonResult<Object> edit(@RequestBody SystemCommonGroupMenuDTO systemCommonGroupMenuDTO) {
        SystemCommonGroupMenu systemCommonGroupMenu = new SystemCommonGroupMenu();
        BeanUtils.copyProperties(systemCommonGroupMenuDTO, systemCommonGroupMenu, DataConstants.IGNORED_ON_COPY);
        return CommonResult.ok(systemCommonGroupMenuService.updateSystemCommonGroupMenu(systemCommonGroupMenu));
    }

    /**
     * 删除组关联的权限
     */
    @Operation(summary = "删除组关联的权限", description = "删除组关联的权限")
    @PreAuthorize(hasPermi = "system:commonGroupMenu:remove")
    @Log(title = "组关联的权限", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/commonGroupMenu/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemCommonGroupMenuService.deleteSystemCommonGroupMenuByIds(ids));
    }
}
