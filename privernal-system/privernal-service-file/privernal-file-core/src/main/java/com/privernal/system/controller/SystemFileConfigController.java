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
import com.privernal.system.domain.SystemFileConfig;
import com.privernal.system.dto.SystemFileConfigDTO;
import com.privernal.system.filter.SystemFileConfigFilterDTO;
import com.privernal.system.service.ISystemFileConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件配置Controller
 *
 * @author saperliu
 * @date 2023-10-16
 * @Description 文件配置的接口
 */
@Tag(name = "文件配置相关接口")
@RestController
public class SystemFileConfigController extends BaseController {
    @Autowired
    private ISystemFileConfigService systemFileConfigService;

    /**
     * 查询文件配置列表
     */
    @Operation(summary = "查询文件配置列表", description = "查询文件配置列表")
    @PreAuthorize(hasPermi = "system:fileConfig:list")
    @PostMapping("/back/system/fileConfig/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemFileConfigFilterDTO systemFileConfig) {
        startPage(systemFileConfig);
        List<SystemFileConfigDTO> list = systemFileConfigService.selectSystemFileConfigList(systemFileConfig);
        List<SystemFileConfigDTO> dtoList = new ArrayList<>();
        for (SystemFileConfigDTO obj : list) {
            SystemFileConfigDTO dto = new SystemFileConfigDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        return getDataTable(dtoList);
    }

    /**
     * 获取文件配置详细信息
     */
    @Operation(summary = "获取文件配置详细信息", description = "获取文件配置详细信息")
    @PreAuthorize(hasPermi = "system:fileConfig:query")
    @GetMapping(value = "/back/system/fileConfig/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemFileConfigService.selectSystemFileConfigById(id));
    }

    /**
     * 新增文件配置
     */
    @Operation(summary = "新增文件配置", description = "新增文件配置")
    @PreAuthorize(hasPermi = "system:fileConfig:add")
    @Log(title = "文件配置", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/fileConfig/save")
    public CommonResult<Object> add(@RequestBody SystemFileConfigDTO systemFileConfigDTO) {
        SystemFileConfig systemFileConfig = new SystemFileConfig();
        BeanUtils.copyProperties(systemFileConfigDTO, systemFileConfig);
        return toAjax(systemFileConfigService.insertSystemFileConfig(systemFileConfig));
    }

    /**
     * 修改文件配置
     */
    @Operation(summary = "修改文件配置", description = "修改文件配置")
    @PreAuthorize(hasPermi = "system:fileConfig:edit")
    @Log(title = "文件配置", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/fileConfig/edit")
    public CommonResult<Object> edit(@RequestBody SystemFileConfigDTO systemFileConfigDTO) {
        SystemFileConfig systemFileConfig = new SystemFileConfig();
        BeanUtils.copyProperties(systemFileConfigDTO, systemFileConfig);
        return toAjax(systemFileConfigService.updateSystemFileConfig(systemFileConfig));
    }

    /**
     * 删除文件配置
     */
    @Operation(summary = "删除文件配置", description = "删除文件配置")
    @PreAuthorize(hasPermi = "system:fileConfig:remove")
    @Log(title = "文件配置", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/fileConfig/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemFileConfigService.deleteSystemFileConfigByIds(ids));
    }
}
