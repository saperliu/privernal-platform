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
import com.privernal.common.core.utils.tree.TreeNodeConfig;
import com.privernal.common.core.utils.tree.TreeUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemAppMenu;
import com.privernal.system.dto.SystemAppMenuDTO;
import com.privernal.system.filter.SystemAppMenuFilterDTO;
import com.privernal.system.service.ISystemAppMenuService;
import com.privernal.system.service.ISystemMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 应用关联的菜单Controller
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用关联的菜单的接口
 */
@Tag(name = "应用关联的菜单相关接口")
@RestController
@Slf4j
public class SystemAppMenuController extends BaseController {
    @Autowired
    private ISystemAppMenuService systemAppMenuService;

    @Autowired
    private ISystemMenuService systemMenuService;

    /**
     * 查询应用关联的菜单列表
     */
    @Operation(summary = "查询应用关联的菜单列表", description = "查询应用关联的菜单列表")
    @PreAuthorize(hasPermi = "system:appMenu:list")
    @PostMapping("/back/system/appMenu/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemAppMenuFilterDTO systemAppMenu) {
        startPage(systemAppMenu);
        List<SystemAppMenuDTO> list = systemAppMenuService.selectSystemAppMenuList(systemAppMenu);
        return getDataTable(list);
    }

    /**
     * 获取菜单树型结构,应用的子表内容
     * 显示应用关联的菜单时。
     */
    @Operation(summary = "查询所有菜单组织成树型", description = "查询所有菜单组织成树型")
    @PreAuthorize(hasPermi = "system:appInfo:list")
    @PostMapping("/back/system/appMenu/tree")
    public CommonResult<?> treeAllMenu(@RequestBody SystemAppMenuFilterDTO systemAppMenu) {
        List<SystemAppMenuDTO> menus = systemAppMenuService.selectAppMenuListByAppId(systemAppMenu);
        //配置
        TreeNodeConfig treeNodeConfig = new TreeNodeConfig();
        //// 自定义属性名 都要默认值的
        treeNodeConfig.setIdKey("menuId");
        menus = TreeUtils.convertToTree(menus, treeNodeConfig, null);
        return CommonResult.ok(menus);
    }

    /**
     * 获取应用关联的菜单详细信息
     */
    @Operation(summary = "获取应用关联的菜单详细信息", description = "获取应用关联的菜单详细信息")
    @PreAuthorize(hasPermi = "system:appMenu:query")
    @GetMapping(value = "/back/system/appMenu/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemAppMenuService.selectSystemAppMenuDetailDTOById(id));
    }

    /**
     * 新增应用关联的菜单
     */
    @Operation(summary = "新增应用关联的菜单", description = "新增应用关联的菜单")
    @PreAuthorize(hasPermi = "system:appMenu:add")
    @Log(title = "应用关联的菜单", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/appMenu/save")
    public CommonResult<Object> add(@RequestBody SystemAppMenuDTO systemAppMenuDTO) {
        SystemAppMenu systemAppMenu = new SystemAppMenu();
        BeanUtils.copyProperties(systemAppMenuDTO, systemAppMenu);
        return CommonResult.ok(systemAppMenuService.insertSystemAppMenu(systemAppMenu));
    }

    /**
     * 修改应用关联的菜单
     */
    @Operation(summary = "修改应用关联的菜单", description = "修改应用关联的菜单")
    @PreAuthorize(hasPermi = "system:appMenu:edit")
    @Log(title = "应用关联的菜单", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/appMenu/edit")
    public CommonResult<Object> edit(@RequestBody SystemAppMenuDTO systemAppMenuDTO) {
        SystemAppMenu systemAppMenu = new SystemAppMenu();
        BeanUtils.copyProperties(systemAppMenuDTO, systemAppMenu);
        return CommonResult.ok(systemAppMenuService.updateSystemAppMenu(systemAppMenu));
    }

    /**
     * 删除应用关联的菜单
     */
    @Operation(summary = "删除应用关联的菜单", description = "删除应用关联的菜单")
    @PreAuthorize(hasPermi = "system:appMenu:remove")
    @Log(title = "应用关联的菜单", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/appMenu/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemAppMenuService.deleteSystemAppMenuByIds(ids));
    }
}
