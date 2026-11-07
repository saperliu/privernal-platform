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
import com.privernal.system.domain.SystemDictionaryType;
import com.privernal.system.dto.SystemDictionaryTypeDTO;
import com.privernal.system.filter.SystemDictionaryTypeFilterDTO;
import com.privernal.system.service.ISystemDictionaryTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 字典类型Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 字典类型的接口
 */
@Tag(name = "字典类型相关接口")
@RestController
public class SystemDictionaryTypeController extends BaseController {
    @Autowired
    private ISystemDictionaryTypeService systemDictionaryTypeService;

    /**
     * 查询字典类型列表
     */
    @Operation(summary = "查询字典类型列表", description = "查询字典类型列表")
    @PreAuthorize(hasPermi = "system:dictionaryType:list")
    @PostMapping("/back/system/dictionaryType/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemDictionaryTypeFilterDTO systemDictionaryType) {
        startPage(systemDictionaryType);
        List<SystemDictionaryType> list = systemDictionaryTypeService.selectSystemDictionaryTypeList(systemDictionaryType);
        List<SystemDictionaryTypeDTO> dtoList = new ArrayList<>();
        for (SystemDictionaryType obj : list) {
            SystemDictionaryTypeDTO dto = new SystemDictionaryTypeDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取字典类型详细信息
     */
    @Operation(summary = "获取字典类型详细信息", description = "获取字典类型详细信息")
    @PreAuthorize(hasPermi = "system:dictionaryType:query")
    @GetMapping(value = "/back/system/dictionaryType/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemDictionaryTypeService.selectSystemDictionaryTypeById(id));
    }

    /**
     * 新增字典类型
     */
    @Operation(summary = "新增字典类型", description = "新增字典类型")
    @PreAuthorize(hasPermi = "system:dictionaryType:add")
    @Log(title = "字典类型", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/dictionaryType/save")
    public CommonResult<Object> add(@RequestBody SystemDictionaryTypeDTO systemDictionaryTypeDTO) {
        SystemDictionaryType systemDictionaryType = new SystemDictionaryType();
        BeanUtils.copyProperties(systemDictionaryTypeDTO, systemDictionaryType);
        return toAjax(systemDictionaryTypeService.insertSystemDictionaryType(systemDictionaryType));
    }

    /**
     * 修改字典类型
     */
    @Operation(summary = "修改字典类型", description = "修改字典类型")
    @PreAuthorize(hasPermi = "system:dictionaryType:edit")
    @Log(title = "字典类型", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/dictionaryType/edit")
    public CommonResult<Object> edit(@RequestBody SystemDictionaryTypeDTO systemDictionaryTypeDTO) {
        SystemDictionaryType systemDictionaryType = new SystemDictionaryType();
        BeanUtils.copyProperties(systemDictionaryTypeDTO, systemDictionaryType);
        return toAjax(systemDictionaryTypeService.updateSystemDictionaryType(systemDictionaryType));
    }

    /**
     * 删除字典类型
     */
    @Operation(summary = "删除字典类型", description = "删除字典类型")
    @PreAuthorize(hasPermi = "system:dictionaryType:remove")
    @Log(title = "字典类型", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/dictionaryType/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemDictionaryTypeService.deleteSystemDictionaryTypeByIds(ids));
    }
}
