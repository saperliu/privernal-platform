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
import org.springframework.beans.BeanUtils;
import com.privernal.common.core.utils.tree.TreeUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemMenu;
import com.privernal.system.dto.SystemMenuDTO;
import com.privernal.system.filter.SystemMenuFilterDTO;
import com.privernal.system.service.ISystemMenuService;
import com.alibaba.fastjson2.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 菜单的接口
 */
@Tag(name = "菜单相关接口")
@RestController
public class SystemMenuController extends BaseController {
    @Autowired
    private ISystemMenuService systemMenuService;

    /**
     * 查询菜单列表
     */
    @Operation(summary = "查询菜单列表", description = "查询菜单列表")
    @PreAuthorize(hasPermi = "system:menu:list")
    @PostMapping("/back/system/menu/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemMenuFilterDTO systemMenu) {
        startPage(systemMenu);
        List<SystemMenu> list = systemMenuService.selectSystemMenuList(systemMenu);
        List<SystemMenuDTO> dtoList = new ArrayList<>();
        for (SystemMenu obj : list) {
            SystemMenuDTO dto = new SystemMenuDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取菜单树型结构
     * 菜单的列表功能使用的接口
     */
    @Operation(summary = "查询所有菜单组织成树型", description = "查询所有菜单组织成树型")
    @PreAuthorize(hasPermi = "system:menu:list")
    @PostMapping("/back/system/menu/tree/all")
    public CommonResult<?> treeAllMenu(@RequestBody SystemMenuFilterDTO systemMenuFilterDTO) {
        List<SystemMenu> menus = systemMenuService.selectSystemMenuList(systemMenuFilterDTO);
//        menus = systemMenuService.getChildPerms(menus, MenuConstants.TOP_MENU_PARENT_ID);
        menus = TreeUtils.convertToTree(menus, MenuConstants.TOP_MENU_PARENT_ID);
        return CommonResult.ok(menus);
    }

    /**
     * 获取菜单树型结构【老版】
     * 根据用户个人及组织查询自有权限的菜单功能
     */
    @Operation(summary = "查询菜单树型", description = "根据用户个人及组织查询自有权限的菜单功能")
    @PreAuthorize(hasPermi = "system:menu:list")
    @PostMapping("/back/system/menu/tree")
    public CommonResult<?> treePersonMenu(@RequestBody SystemMenuFilterDTO systemMenuFilterDTO) {
        List<SystemMenu> menus = systemMenuService.getMenuButtonTreeByUserId(SecurityUtils.getUserId(), SecurityUtils.getOrganizationId());
        return CommonResult.ok(menus);
    }

    /**
     * 获取菜单树型结构，
     * 根据用户个人、组织、应用查询自有权限的菜单功能
     */
    @Operation(summary = "查询菜单树型", description = "根据用户个人、组织、应用查询自有权限的菜单功能")
    @PreAuthorize(hasPermi = "system:menu:list")
    @PostMapping("/back/system/menu/app/tree")
    public CommonResult<?> treeAppMenu(@RequestBody SystemMenuFilterDTO systemMenuFilterDTO) {
        List<SystemMenuDTO> menus = systemMenuService.getMenuButtonTreeByUserIdAndAppId(SecurityUtils.getUserId(), SecurityUtils.getOrganizationId(), systemMenuFilterDTO.getAppCode());
        return CommonResult.ok(menus);
    }

    /**
     * 获取菜单详细信息
     */
    @Operation(summary = "获取菜单详细信息", description = "获取菜单详细信息")
    @PreAuthorize(hasPermi = "system:menu:query")
    @GetMapping(value = "/back/system/menu/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemMenuService.selectSystemMenuById(id));
    }

    /**
     * 新增菜单
     */
    @Operation(summary = "新增菜单", description = "新增菜单")
    @PreAuthorize(hasPermi = "system:menu:add")
    @Log(title = "菜单", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/menu/save")
    public CommonResult<Object> add(@RequestBody SystemMenuDTO systemMenuDTO) {
        SystemMenu systemMenu = new SystemMenu();
        BeanUtils.copyProperties(systemMenuDTO, systemMenu);
        return toAjax(systemMenuService.insertSystemMenu(systemMenu));
    }

    /**
     * 修改菜单
     */
    @Operation(summary = "修改菜单", description = "修改菜单")
    @PreAuthorize(hasPermi = "system:menu:edit")
    @Log(title = "菜单", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/menu/edit")
    public CommonResult<Object> edit(@RequestBody SystemMenuDTO systemMenuDTO) {
        SystemMenu systemMenu = new SystemMenu();
        BeanUtils.copyProperties(systemMenuDTO, systemMenu);
        return toAjax(systemMenuService.updateSystemMenu(systemMenu));
    }

    /**
     * 删除菜单
     */
    @Operation(summary = "删除菜单", description = "删除菜单")
    @PreAuthorize(hasPermi = "system:menu:remove")
    @Log(title = "菜单", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/menu/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemMenuService.deleteSystemMenuByIds(ids));
    }

    /**
     * 获取路由信息【老版】
     * 左侧显示所有菜单
     *
     * @return 路由信息
     */
    @GetMapping("/back/system/menu/getRouters")
    public CommonResult<Object> getRouters() {
        String userId = SecurityUtils.getUserId();
        String organizationId = SecurityUtils.getOrganizationId();
        List<SystemMenu> menus = systemMenuService.getRoutersByUserId(userId, organizationId);
        Map<String, Object> res = new HashMap<>();
        res.put("routes", systemMenuService.buildMenus(menus));
        //必须要有一个默认首页，否则前端无法显示。
        res.put("home", "workbench");
        return CommonResult.ok(res);
    }

    /**
     * 获取路由信息[新版]
     * 通过应用查询路由列表
     * 左侧只显示应用的关联的菜单
     *
     * @return 路由信息
     */
    @GetMapping("/back/system/menu/app/getRouters")
    public CommonResult<Object> getRouters(@RequestParam("id") String appId, @RequestParam("code") String code) {
        String userId = SecurityUtils.getUserId();
        String organizationId = SecurityUtils.getOrganizationId();
        List<SystemMenuDTO> menus = systemMenuService.getRoutersByUserIdAndAppId(userId, organizationId, appId, code);
        Map<String, Object> res = new HashMap<>();
        logger.info("----------getRouters app menus :  {} ", JSON.toJSONString(menus));
        res.put("routes", systemMenuService.buildMenuDTOs(menus));
        return CommonResult.ok(res);
    }

    /**
     * APP获取工作台的菜单
     *
     * @return 路由信息
     */
    @GetMapping("/app/system/menu/getAppMenus")
    public CommonResult<Object> getAppMenus() {
        String userId = SecurityUtils.getUserId();
        String organizationId = SecurityUtils.getOrganizationId();
        List<SystemMenu> menus = systemMenuService.getAuthAppMenuByUserId(userId, organizationId);
        return CommonResult.ok(menus);
    }
}
