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
import com.privernal.system.domain.SystemRoleMenu;
import com.privernal.system.dto.SystemRoleMenuDTO;
import com.privernal.system.filter.SystemRoleMenuFilterDTO;
import com.privernal.system.service.ISystemRoleMenuService;
import com.alibaba.fastjson2.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色与菜单关系Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色与菜单关系的接口
 */
@Tag(name = "角色与菜单关系相关接口")
@RestController
public class SystemRoleMenuController extends BaseController {
    @Autowired
    private ISystemRoleMenuService systemRoleMenuService;

    /**
     * 查询角色与菜单关系列表
     */
    @Operation(summary = "查询角色与菜单关系列表", description = "查询角色与菜单关系列表")
    @PreAuthorize(hasPermi = "system:roleMenu:list")
    @PostMapping("/back/system/roleMenu/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemRoleMenuFilterDTO systemRoleMenu) {
        startPage(systemRoleMenu);
        List<SystemRoleMenu> list = systemRoleMenuService.selectSystemRoleMenuList(systemRoleMenu);
        List<SystemRoleMenuDTO> dtoList = new ArrayList<>();
        for (SystemRoleMenu obj : list) {
            SystemRoleMenuDTO dto = new SystemRoleMenuDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取角色与菜单关系详细信息
     */
    @Operation(summary = "获取角色与菜单关系详细信息", description = "获取角色与菜单关系详细信息")
    @PreAuthorize(hasPermi = "system:roleMenu:query")
    @GetMapping(value = "/back/system/roleMenu/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemRoleMenuService.selectSystemRoleMenuById(id));
    }

    /**
     * 新增角色与菜单关系
     */
    @Operation(summary = "新增角色与菜单关系", description = "新增角色与菜单关系")
    @PreAuthorize(hasPermi = "system:roleMenu:add")
    @Log(title = "角色与菜单关系", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/roleMenu/save")
    public CommonResult<Object> add(@RequestBody SystemRoleMenuDTO systemRoleMenuDTO) {
        SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
        BeanUtils.copyProperties(systemRoleMenuDTO, systemRoleMenu);
        return toAjax(systemRoleMenuService.insertSystemRoleMenu(systemRoleMenu));
    }

    /**
     * 修改角色与菜单关系
     */
    @Operation(summary = "修改角色与菜单关系", description = "修改角色与菜单关系")
    @PreAuthorize(hasPermi = "system:roleMenu:edit")
    @Log(title = "角色与菜单关系", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/roleMenu/edit")
    public CommonResult<Object> edit(@RequestBody SystemRoleMenuDTO systemRoleMenuDTO) {
        SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
        BeanUtils.copyProperties(systemRoleMenuDTO, systemRoleMenu);
        return toAjax(systemRoleMenuService.updateSystemRoleMenu(systemRoleMenu));
    }

    /**
     * 删除角色与菜单关系
     */
    @Operation(summary = "删除角色与菜单关系", description = "删除角色与菜单关系")
    @PreAuthorize(hasPermi = "system:roleMenu:remove")
    @Log(title = "角色与菜单关系", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/roleMenu/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemRoleMenuService.deleteSystemRoleMenuByIds(ids));
    }

    /**
     * 新增角色与菜单关系
     */
    @Operation(summary = "新增角色与菜单关系", description = "新增角色与菜单关系")
    @PreAuthorize(hasPermi = "system:roleMenu:add")
    @Log(title = "角色与菜单关系", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/roleMenu/batch/save")
    public CommonResult<Object> addBatch(@RequestBody SystemRoleMenuDTO systemRoleMenuDTO) {
        logger.info("------roleMenu/batch/save  {} ", JSON.toJSONString(systemRoleMenuDTO));
        return toAjax(systemRoleMenuService.insertBatchSystemRoleMenu(systemRoleMenuDTO));
    }
}
