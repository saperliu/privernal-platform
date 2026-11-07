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
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemDefaultConfig;
import com.privernal.system.dto.AppSystemConfigDTO;
import com.privernal.system.dto.SystemDefaultConfigDTO;
import com.privernal.system.filter.SystemDefaultConfigFilterDTO;
import com.privernal.system.service.ISystemDefaultConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统配置Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 系统配置的接口
 */
@Tag(name = "系统配置相关接口")
@RestController
public class SystemDefaultConfigController extends BaseController {
    @Autowired
    private ISystemDefaultConfigService systemDefaultConfigService;

    /**
     * 查询系统配置列表
     */
    @Operation(summary = "查询系统配置列表", description = "查询系统配置列表")
    @PreAuthorize(hasPermi = "system:defaultConfig:list")
    @PostMapping("/back/system/defaultConfig/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemDefaultConfigFilterDTO systemDefaultConfig) {
        startPage(systemDefaultConfig);
        List<SystemDefaultConfig> list = systemDefaultConfigService.selectSystemDefaultConfigList(systemDefaultConfig);
        List<SystemDefaultConfigDTO> dtoList = new ArrayList<>();
        for (SystemDefaultConfig obj : list) {
            SystemDefaultConfigDTO dto = new SystemDefaultConfigDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取系统配置详细信息
     */
    @Operation(summary = "获取系统配置详细信息", description = "获取系统配置详细信息")
    @PreAuthorize(hasPermi = "system:defaultConfig:query")
    @GetMapping(value = "/back/system/defaultConfig/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemDefaultConfigService.selectSystemDefaultConfigById(id));
    }

    /**
     * 新增系统配置
     */
    @Operation(summary = "新增系统配置", description = "新增系统配置")
    @PreAuthorize(hasPermi = "system:defaultConfig:add")
    @Log(title = "系统配置", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/defaultConfig/save")
    public CommonResult<Object> add(@RequestBody SystemDefaultConfigDTO systemDefaultConfigDTO) {
        SystemDefaultConfig systemDefaultConfig = new SystemDefaultConfig();
        BeanUtils.copyProperties(systemDefaultConfigDTO, systemDefaultConfig);
        return toAjax(systemDefaultConfigService.insertSystemDefaultConfig(systemDefaultConfig));
    }

    /**
     * 修改系统配置
     */
    @Operation(summary = "修改系统配置", description = "修改系统配置")
    @PreAuthorize(hasPermi = "system:defaultConfig:edit")
    @Log(title = "系统配置", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/defaultConfig/edit")
    public CommonResult<Object> edit(@RequestBody SystemDefaultConfigDTO systemDefaultConfigDTO) {
        SystemDefaultConfig systemDefaultConfig = new SystemDefaultConfig();
        BeanUtils.copyProperties(systemDefaultConfigDTO, systemDefaultConfig);
        return toAjax(systemDefaultConfigService.updateSystemDefaultConfig(systemDefaultConfig));
    }

    /**
     * 删除系统配置
     */
    @Operation(summary = "删除系统配置", description = "删除系统配置")
    @PreAuthorize(hasPermi = "system:defaultConfig:remove")
    @Log(title = "系统配置", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/defaultConfig/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemDefaultConfigService.deleteSystemDefaultConfigByIds(ids));
    }

    /**
     * 查询系统配置的APP端的访问网址
     * 没有登录时查询，所以不需要权限
     */
    @Operation(summary = "查询系统配置列表", description = "查询系统配置列表")
    @GetMapping("/app/system/defaultConfig/net/list")
    public CommonResult<Object> netList(@RequestParam("key") String key) {
        boolean ignore = OrganizationContextHolder.isIgnore();
        try {
            // 数据不区分组织，而是根据不同的名称登录不同的组织
            OrganizationContextHolder.setIgnore(true);
            List<SystemDefaultConfig<AppSystemConfigDTO>> list = systemDefaultConfigService.selectNetConfigList();
            return CommonResult.ok(list);
        } finally {
            OrganizationContextHolder.setIgnore(ignore);
            OrganizationContextHolder.clear();
        }
    }

    /**
     * 查询平台的样式配置
     */
    @Operation(summary = "查询系统配置列表", description = "查询系统配置列表")
    @GetMapping("/back/system/defaultConfig/theme/{key}")
    public CommonResult<Object> themeList(@PathVariable("key") String key) {
        if (StringUtils.isNotEmpty(SecurityUtils.getOrganizationId())) {
            // 查询该组织的平台样式
            List<SystemDefaultConfig> list = systemDefaultConfigService.selectSystemDefaultConfigList(key);
            return CommonResult.ok(list);
        } else {
            return CommonResult.fail("请登录");
        }
    }

}
