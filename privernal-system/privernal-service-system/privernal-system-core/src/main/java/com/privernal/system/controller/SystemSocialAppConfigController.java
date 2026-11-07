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
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.annotation.ServiceRequest;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemSocialAppConfig;
import com.privernal.system.dto.SystemSocialAppConfigDTO;
import com.privernal.system.filter.SystemSocialAppConfigFilterDTO;
import com.privernal.system.service.ISystemSocialAppConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 第三方应用配置(小程序)Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 第三方应用配置(小程序)的接口
 */
@Tag(name = "第三方应用配置(小程序)相关接口")
@RestController
public class SystemSocialAppConfigController extends BaseController {
    @Autowired
    private ISystemSocialAppConfigService systemSocialAppConfigService;

    /**
     * 查询第三方应用配置(小程序)列表
     */
    @Operation(summary = "查询第三方应用配置(小程序)列表", description = "查询第三方应用配置(小程序)列表")
    @PreAuthorize(hasPermi = "system:appConfig:list")
    @PostMapping("/back/system/appConfig/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemSocialAppConfigFilterDTO systemSocialAppConfig) {
        startPage(systemSocialAppConfig);
        List<SystemSocialAppConfig> list = new ArrayList<>();
        //超级管理可以查询其它组织的应用集成配置, 在集中化配置时使用。方便前端配置。
        if (SecurityUtils.isSuperPlatformRoleUser()) {
            boolean ignore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                list = systemSocialAppConfigService.selectSystemSocialAppConfigList(systemSocialAppConfig);
            } finally {
                OrganizationContextHolder.setIgnore(ignore);
                OrganizationContextHolder.clear();
            }
        } else {
            list = systemSocialAppConfigService.selectSystemSocialAppConfigList(systemSocialAppConfig);
        }

        List<SystemSocialAppConfigDTO> dtoList = new ArrayList<>();
        for (SystemSocialAppConfig obj : list) {
            SystemSocialAppConfigDTO dto = new SystemSocialAppConfigDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取第三方应用配置(小程序)详细信息
     */
    @Operation(summary = "获取第三方应用配置(小程序)详细信息", description = "获取第三方应用配置(小程序)详细信息")
    @PreAuthorize(hasPermi = "system:appConfig:query")
    @GetMapping(value = "/back/system/appConfig/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemSocialAppConfigService.selectSystemSocialAppConfigById(id));
    }

    /**
     * 新增第三方应用配置(小程序)
     */
    @Operation(summary = "新增第三方应用配置(小程序)", description = "新增第三方应用配置(小程序)")
    @PreAuthorize(hasPermi = "system:appConfig:add")
    @Log(title = "第三方应用配置(小程序)", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/appConfig/save")
    public CommonResult<Object> add(@RequestBody SystemSocialAppConfigDTO systemSocialAppConfigDTO) {
        SystemSocialAppConfig systemSocialAppConfig = new SystemSocialAppConfig();
        BeanUtils.copyProperties(systemSocialAppConfigDTO, systemSocialAppConfig);
        return toAjax(systemSocialAppConfigService.insertSystemSocialAppConfig(systemSocialAppConfig));
    }

    /**
     * 修改第三方应用配置(小程序)
     */
    @Operation(summary = "修改第三方应用配置(小程序)", description = "修改第三方应用配置(小程序)")
    @PreAuthorize(hasPermi = "system:appConfig:edit")
    @Log(title = "第三方应用配置(小程序)", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/appConfig/edit")
    public CommonResult<Object> edit(@RequestBody SystemSocialAppConfigDTO systemSocialAppConfigDTO) {
        SystemSocialAppConfig systemSocialAppConfig = new SystemSocialAppConfig();
        BeanUtils.copyProperties(systemSocialAppConfigDTO, systemSocialAppConfig);
        return toAjax(systemSocialAppConfigService.updateSystemSocialAppConfig(systemSocialAppConfig));
    }

    /**
     * 删除第三方应用配置(小程序)
     */
    @Operation(summary = "删除第三方应用配置(小程序)", description = "删除第三方应用配置(小程序)")
    @PreAuthorize(hasPermi = "system:appConfig:remove")
    @Log(title = "第三方应用配置(小程序)", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/appConfig/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemSocialAppConfigService.deleteSystemSocialAppConfigByIds(ids));
    }

    /**
     * 获取第三方应用配置(小程序)详细信息
     */
    @ServiceRequest
    @GetMapping(value = "/inner/social/sysSocialAppConfig/{appCode}")
    public CommonResult<SystemSocialAppConfig> getAppConfig(@PathVariable("appCode") String appCode) {
        return CommonResult.ok(systemSocialAppConfigService.selectSysSocialAppConfigByCode(appCode));
    }

}
