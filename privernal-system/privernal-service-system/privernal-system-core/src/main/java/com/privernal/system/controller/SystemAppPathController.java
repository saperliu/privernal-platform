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
import com.privernal.system.domain.SystemAppPath;
import com.privernal.system.dto.SystemAppPathDTO;
import com.privernal.system.filter.SystemAppPathFilterDTO;
import com.privernal.system.service.ISystemAppPathService;
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
 * 应用可以访问的路径Controller
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用可以访问的路径的接口
 */
@Tag(name = "应用可以访问的路径相关接口")
@RestController
@Slf4j
public class SystemAppPathController extends BaseController {
    @Autowired
    private ISystemAppPathService systemAppPathService;

    /**
     * 查询应用可以访问的路径列表
     */
    @Operation(summary = "查询应用可以访问的路径列表", description = "查询应用可以访问的路径列表")
    @PreAuthorize(hasPermi = "system:appPath:list")
    @PostMapping("/back/system/appPath/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemAppPathFilterDTO systemAppPath) {
        startPage(systemAppPath);
        List<SystemAppPathDTO> list = systemAppPathService.selectSystemAppPathList(systemAppPath);
        List<SystemAppPathDTO> dtoList = new ArrayList<>();
        for (SystemAppPathDTO obj : list) {
            SystemAppPathDTO dto = new SystemAppPathDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        return getDataTable(dtoList);
    }

    /**
     * 获取应用可以访问的路径详细信息
     */
    @Operation(summary = "获取应用可以访问的路径详细信息", description = "获取应用可以访问的路径详细信息")
    @PreAuthorize(hasPermi = "system:appPath:query")
    @GetMapping(value = "/back/system/appPath/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemAppPathService.selectSystemAppPathDetailDTOById(id));
    }

    /**
     * 新增应用可以访问的路径
     */
    @Operation(summary = "新增应用可以访问的路径", description = "新增应用可以访问的路径")
    @PreAuthorize(hasPermi = "system:appPath:add")
    @Log(title = "应用可以访问的路径", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/appPath/save")
    public CommonResult<Object> add(@RequestBody SystemAppPathDTO systemAppPathDTO) {
        SystemAppPath systemAppPath = new SystemAppPath();
        BeanUtils.copyProperties(systemAppPathDTO, systemAppPath);
        return CommonResult.ok(systemAppPathService.insertSystemAppPath(systemAppPath));
    }

    /**
     * 修改应用可以访问的路径
     */
    @Operation(summary = "修改应用可以访问的路径", description = "修改应用可以访问的路径")
    @PreAuthorize(hasPermi = "system:appPath:edit")
    @Log(title = "应用可以访问的路径", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/appPath/edit")
    public CommonResult<Object> edit(@RequestBody SystemAppPathDTO systemAppPathDTO) {
        SystemAppPath systemAppPath = new SystemAppPath();
        BeanUtils.copyProperties(systemAppPathDTO, systemAppPath);
        return CommonResult.ok(systemAppPathService.updateSystemAppPath(systemAppPath));
    }

    /**
     * 删除应用可以访问的路径
     */
    @Operation(summary = "删除应用可以访问的路径", description = "删除应用可以访问的路径")
    @PreAuthorize(hasPermi = "system:appPath:remove")
    @Log(title = "应用可以访问的路径", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/appPath/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemAppPathService.deleteSystemAppPathByIds(ids));
    }
}
