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
import com.privernal.system.domain.SystemUserAppMenu;
import com.privernal.system.dto.SystemUserAppMenuDTO;
import com.privernal.system.filter.SystemUserAppMenuFilterDTO;
import com.privernal.system.service.ISystemUserAppMenuService;
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
 * 用户收藏的常用功能菜单Controller
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 用户收藏的常用功能菜单的接口
 */
@Tag(name = "用户收藏的常用功能菜单相关接口")
@RestController
@Slf4j
public class SystemUserAppMenuController extends BaseController {
    @Autowired
    private ISystemUserAppMenuService systemUserAppMenuService;

    /**
     * 查询用户收藏的常用功能菜单列表
     */
    @Operation(summary = "查询用户收藏的常用功能菜单列表", description = "查询用户收藏的常用功能菜单列表")
    @PreAuthorize(hasPermi = "system:userAppMenu:list")
    @PostMapping("/back/system/userAppMenu/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemUserAppMenuFilterDTO systemUserAppMenu) {
        startPage(systemUserAppMenu);
        List<SystemUserAppMenuDTO> list = systemUserAppMenuService.selectSystemUserAppMenuList(systemUserAppMenu);
        List<SystemUserAppMenuDTO> dtoList = new ArrayList<>();
        for (SystemUserAppMenuDTO obj : list) {
            SystemUserAppMenuDTO dto = new SystemUserAppMenuDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        return getDataTable(dtoList);
    }

    /**
     * 获取用户收藏的常用功能菜单详细信息
     */
    @Operation(summary = "获取用户收藏的常用功能菜单详细信息", description = "获取用户收藏的常用功能菜单详细信息")
    @PreAuthorize(hasPermi = "system:userAppMenu:query")
    @GetMapping(value = "/back/system/userAppMenu/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemUserAppMenuService.selectSystemUserAppMenuDetailDTOById(id));
    }

    /**
     * 新增用户收藏的常用功能菜单
     */
    @Operation(summary = "新增用户收藏的常用功能菜单", description = "新增用户收藏的常用功能菜单")
    @PreAuthorize(hasPermi = "system:userAppMenu:add")
    @Log(title = "用户收藏的常用功能菜单", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/userAppMenu/save")
    public CommonResult<Object> add(@RequestBody SystemUserAppMenuDTO systemUserAppMenuDTO) {
        SystemUserAppMenu systemUserAppMenu = new SystemUserAppMenu();
        BeanUtils.copyProperties(systemUserAppMenuDTO, systemUserAppMenu);
        return CommonResult.ok(systemUserAppMenuService.insertSystemUserAppMenu(systemUserAppMenu));
    }

    /**
     * 修改用户收藏的常用功能菜单
     */
    @Operation(summary = "修改用户收藏的常用功能菜单", description = "修改用户收藏的常用功能菜单")
    @PreAuthorize(hasPermi = "system:userAppMenu:edit")
    @Log(title = "用户收藏的常用功能菜单", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/userAppMenu/edit")
    public CommonResult<Object> edit(@RequestBody SystemUserAppMenuDTO systemUserAppMenuDTO) {
        SystemUserAppMenu systemUserAppMenu = new SystemUserAppMenu();
        BeanUtils.copyProperties(systemUserAppMenuDTO, systemUserAppMenu);
        return CommonResult.ok(systemUserAppMenuService.updateSystemUserAppMenu(systemUserAppMenu));
    }

    /**
     * 删除用户收藏的常用功能菜单
     */
    @Operation(summary = "删除用户收藏的常用功能菜单", description = "删除用户收藏的常用功能菜单")
    @PreAuthorize(hasPermi = "system:userAppMenu:remove")
    @Log(title = "用户收藏的常用功能菜单", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/userAppMenu/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemUserAppMenuService.deleteSystemUserAppMenuByIds(ids));
    }
}
