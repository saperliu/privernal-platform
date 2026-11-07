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
import com.privernal.system.domain.SystemDictionaryData;
import com.privernal.system.dto.SystemDictionaryDataDTO;
import com.privernal.system.filter.SystemDictionaryDataFilterDTO;
import com.privernal.system.service.ISystemDictionaryDataService;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 字典数据Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 字典数据的接口
 */
@Tag(name = "字典数据相关接口")
@RestController
public class SystemDictionaryDataController extends BaseController {
    @Autowired
    private ISystemDictionaryDataService systemDictionaryDataService;

    /**
     * 查询字典数据列表
     */
    @Operation(summary = "查询字典数据列表", description = "查询字典数据列表")
    @PreAuthorize(hasPermi = "system:dictionaryData:list")
    @PostMapping("/back/system/dictionaryData/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemDictionaryDataFilterDTO systemDictionaryData) {
        startPage(systemDictionaryData);
        List<SystemDictionaryData> list = systemDictionaryDataService.selectSystemDictionaryDataList(systemDictionaryData);
        List<SystemDictionaryDataDTO> dtoList = new ArrayList<>();
        for (SystemDictionaryData obj : list) {
            SystemDictionaryDataDTO dto = new SystemDictionaryDataDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    @Operation(summary = "根据类型查询字典数据", description = "获取字典数据详细信息")
    @PreAuthorize(hasPermi = "system:dictionaryData:query")
    @GetMapping(value = "/inner/system/dictionaryData/{dictType}")
    public CommonResult<List<SystemDictionaryData>> findDictionaryDataByType(@PathVariable("dictType") String dictType) {

        SystemDictionaryDataFilterDTO systemDictionaryDataFilterDTO = new SystemDictionaryDataFilterDTO();
        systemDictionaryDataFilterDTO.setDictType(dictType);
        List<SystemDictionaryData> list = systemDictionaryDataService.selectSystemDictionaryDataList(systemDictionaryDataFilterDTO);

        return CommonResult.ok(list);
    }

    /**
     * 获取字典数据详细信息
     */
    @Operation(summary = "获取字典数据详细信息", description = "获取字典数据详细信息")
    @PreAuthorize(hasPermi = "system:dictionaryData:query")
    @GetMapping(value = "/back/system/dictionaryData/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemDictionaryDataService.selectSystemDictionaryDataById(id));
    }

    /**
     * 新增字典数据
     */
    @Operation(summary = "新增字典数据", description = "新增字典数据")
    @PreAuthorize(hasPermi = "system:dictionaryData:add")
    @Log(title = "字典数据", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/dictionaryData/save")
    public CommonResult<Object> add(@RequestBody SystemDictionaryDataDTO systemDictionaryDataDTO) {
        SystemDictionaryData systemDictionaryData = new SystemDictionaryData();
        BeanUtils.copyProperties(systemDictionaryDataDTO, systemDictionaryData);
        return toAjax(systemDictionaryDataService.insertSystemDictionaryData(systemDictionaryData));
    }

    /**
     * 修改字典数据
     */
    @Operation(summary = "修改字典数据", description = "修改字典数据")
    @PreAuthorize(hasPermi = "system:dictionaryData:edit")
    @Log(title = "字典数据", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/dictionaryData/edit")
    public CommonResult<Object> edit(@RequestBody SystemDictionaryDataDTO systemDictionaryDataDTO) {
        SystemDictionaryData systemDictionaryData = new SystemDictionaryData();
        BeanUtils.copyProperties(systemDictionaryDataDTO, systemDictionaryData);
        return toAjax(systemDictionaryDataService.updateSystemDictionaryData(systemDictionaryData));
    }

    /**
     * 删除字典数据
     */
    @Operation(summary = "删除字典数据", description = "删除字典数据")
    @PreAuthorize(hasPermi = "system:dictionaryData:remove")
    @Log(title = "字典数据", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/dictionaryData/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemDictionaryDataService.deleteSystemDictionaryDataByIds(ids));
    }

    @ServiceRequest
    @Operation(summary = "获取字典数据详细信息", description = "获取字典数据详细信息")
    @GetMapping(value = "/inner/system/dictionaryData/{dictType}/{dictValue}")
    public CommonResult<Object> findSystemDictionaryDataByTypeAndValue(@PathVariable("dictType") String dictType, @PathVariable("dictValue") String dictValue) {
        return CommonResult.ok(systemDictionaryDataService.findSystemDictionaryDataByTypeAndValue(dictType, dictValue));
    }

    /**
     * 根据字典类型和字典值查询字典数据,并根据JSON字段存储格式返回JSON数据
     */
    @ServiceRequest
    @Operation(summary = "根据字典类型和字典值查询字典数据,并根据JSON字段存储格式返回JSON数据", description = "获取字典数据详细信息")
    @GetMapping(value = "/inner/system/dictionaryData/json/{dictType}/{dictValue}/{organizationId}")
    public CommonResult<JSONObject> findSelectSystemDictionaryJSONDataByTypeAndValue(@PathVariable("organizationId") String organizationId, @PathVariable("dictType") String dictType, @PathVariable("dictValue") String dictValue) {
        boolean ignore = OrganizationContextHolder.isIgnore();
        try {
            if (StringUtils.isNotEmpty(organizationId)) {
//                OrganizationContextHolder.setIgnore(true);
                OrganizationContextHolder.setOrganizationId(organizationId);
            }
            return CommonResult.ok(systemDictionaryDataService.findSelectSystemDictionaryJSONDataByTypeAndValue(dictType, dictValue));
        } finally {
            OrganizationContextHolder.setIgnore(ignore);
            OrganizationContextHolder.clear();
        }
    }

}
