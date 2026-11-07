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
import com.privernal.system.domain.SystemOrganizationApp;
import com.privernal.system.dto.SystemOrganizationAppDTO;
import com.privernal.system.filter.SystemOrganizationAppFilterDTO;
import com.privernal.system.service.ISystemOrganizationAppService;
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
 * 组织授权的应用Controller
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 组织授权的应用的接口
 */
@Tag(name = "组织授权的应用相关接口")
@RestController
@Slf4j
public class SystemOrganizationAppController extends BaseController {
    @Autowired
    private ISystemOrganizationAppService systemOrganizationAppService;

    /**
     * 查询组织授权的应用列表
     */
    @Operation(summary = "查询组织授权的应用列表", description = "查询组织授权的应用列表")
    @PreAuthorize(hasPermi = "system:organizationApp:list")
    @PostMapping("/back/system/organizationApp/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemOrganizationAppFilterDTO systemOrganizationApp) {
        startPage(systemOrganizationApp);
        List<SystemOrganizationAppDTO> list = systemOrganizationAppService.selectSystemOrganizationAppList(systemOrganizationApp);
        List<SystemOrganizationAppDTO> dtoList = new ArrayList<>();
        for (SystemOrganizationAppDTO obj : list) {
            SystemOrganizationAppDTO dto = new SystemOrganizationAppDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        return getDataTable(dtoList);
    }

    /**
     * 获取组织授权的应用详细信息
     */
    @Operation(summary = "获取组织授权的应用详细信息", description = "获取组织授权的应用详细信息")
    @PreAuthorize(hasPermi = "system:organizationApp:query")
    @GetMapping(value = "/back/system/organizationApp/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemOrganizationAppService.selectSystemOrganizationAppDetailDTOById(id));
    }

    /**
     * 新增组织授权的应用
     */
    @Operation(summary = "新增组织授权的应用", description = "新增组织授权的应用")
    @PreAuthorize(hasPermi = "system:organizationApp:add")
    @Log(title = "组织授权的应用", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/organizationApp/save")
    public CommonResult<Object> add(@RequestBody SystemOrganizationAppDTO systemOrganizationAppDTO) {
        SystemOrganizationApp systemOrganizationApp = new SystemOrganizationApp();
        BeanUtils.copyProperties(systemOrganizationAppDTO, systemOrganizationApp);
        return CommonResult.ok(systemOrganizationAppService.insertSystemOrganizationApp(systemOrganizationApp));
    }

    /**
     * 修改组织授权的应用
     */
    @Operation(summary = "修改组织授权的应用", description = "修改组织授权的应用")
    @PreAuthorize(hasPermi = "system:organizationApp:edit")
    @Log(title = "组织授权的应用", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/organizationApp/edit")
    public CommonResult<Object> edit(@RequestBody SystemOrganizationAppDTO systemOrganizationAppDTO) {
        SystemOrganizationApp systemOrganizationApp = new SystemOrganizationApp();
        BeanUtils.copyProperties(systemOrganizationAppDTO, systemOrganizationApp);
        return CommonResult.ok(systemOrganizationAppService.updateSystemOrganizationApp(systemOrganizationApp));
    }

    /**
     * 删除组织授权的应用
     */
    @Operation(summary = "删除组织授权的应用", description = "删除组织授权的应用")
    @PreAuthorize(hasPermi = "system:organizationApp:remove")
    @Log(title = "组织授权的应用", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/organizationApp/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemOrganizationAppService.deleteSystemOrganizationAppByIds(ids));
    }
}
