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
import com.privernal.system.domain.SystemUserServiceOrg;
import com.privernal.system.dto.SystemUserServiceOrgDTO;
import com.privernal.system.filter.SystemUserServiceOrgFilterDTO;
import com.privernal.system.service.ISystemUserServiceOrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户服务组织Controller
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户服务组织的接口
 */
@Tag(name = "用户服务组织相关接口")
@RestController
public class SystemUserServiceOrgController extends BaseController {
    @Autowired
    private ISystemUserServiceOrgService systemUserServiceOrgService;

    /**
     * 查询用户服务组织列表
     */
    @Operation(summary = "查询用户服务组织列表", description = "查询用户服务组织列表")
    @PreAuthorize(hasPermi = "system:userServiceOrg:list")
    @PostMapping("/back/system/userServiceOrg/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemUserServiceOrgFilterDTO systemUserServiceOrg) {
        startPage(systemUserServiceOrg);
        List<SystemUserServiceOrg> list = systemUserServiceOrgService.selectSystemUserServiceOrgList(systemUserServiceOrg);
        List<SystemUserServiceOrgDTO> dtoList = new ArrayList<>();
        for (SystemUserServiceOrg obj : list) {
            SystemUserServiceOrgDTO dto = new SystemUserServiceOrgDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取用户服务组织详细信息
     */
    @Operation(summary = "获取用户服务组织详细信息", description = "获取用户服务组织详细信息")
    @PreAuthorize(hasPermi = "system:userServiceOrg:query")
    @GetMapping(value = "/back/system/userServiceOrg/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemUserServiceOrgService.selectSystemUserServiceOrgById(id));
    }

    /**
     * 新增用户服务组织
     */
    @Operation(summary = "新增用户服务组织", description = "新增用户服务组织")
    @PreAuthorize(hasPermi = "system:userServiceOrg:add")
    @Log(title = "用户服务组织", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/userServiceOrg/save")
    public CommonResult<Object> add(@RequestBody SystemUserServiceOrgDTO systemUserServiceOrgDTO) {
        SystemUserServiceOrg systemUserServiceOrg = new SystemUserServiceOrg();
        BeanUtils.copyProperties(systemUserServiceOrgDTO, systemUserServiceOrg);
        return toAjax(systemUserServiceOrgService.insertSystemUserServiceOrg(systemUserServiceOrg));
    }

    /**
     * 修改用户服务组织
     */
    @Operation(summary = "修改用户服务组织", description = "修改用户服务组织")
    @PreAuthorize(hasPermi = "system:userServiceOrg:edit")
    @Log(title = "用户服务组织", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/userServiceOrg/edit")
    public CommonResult<Object> edit(@RequestBody SystemUserServiceOrgDTO systemUserServiceOrgDTO) {
        SystemUserServiceOrg systemUserServiceOrg = new SystemUserServiceOrg();
        BeanUtils.copyProperties(systemUserServiceOrgDTO, systemUserServiceOrg);
        return toAjax(systemUserServiceOrgService.updateSystemUserServiceOrg(systemUserServiceOrg));
    }

    /**
     * 删除用户服务组织
     */
    @Operation(summary = "删除用户服务组织", description = "删除用户服务组织")
    @PreAuthorize(hasPermi = "system:userServiceOrg:remove")
    @Log(title = "用户服务组织", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/userServiceOrg/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemUserServiceOrgService.deleteSystemUserServiceOrgByIds(ids));
    }
}
