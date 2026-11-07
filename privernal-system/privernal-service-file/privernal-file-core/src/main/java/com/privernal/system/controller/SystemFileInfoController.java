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
import com.privernal.common.core.enums.ProductModuleEnums;
import org.springframework.beans.BeanUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.config.OssConfig;
import com.privernal.system.constant.UploadConstants;
import com.privernal.system.domain.SystemFileInfo;
import com.privernal.system.dto.SystemFileInfoDTO;
import com.privernal.system.filter.SystemFileInfoFilterDTO;
import com.privernal.system.service.ISystemFileInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件信息Controller
 *
 * @author saperliu
 * @date 2023-10-16
 * @Description 文件信息的接口
 */
@Tag(name = "文件信息相关接口")
@RestController
public class SystemFileInfoController extends BaseController {
    @Autowired
    private ISystemFileInfoService systemFileInfoService;

    /**
     * 域名或本机访问地址
     */
    @Value("${file.domain}")
    public String domain;

    @Autowired
    private OssConfig ossConfig;

    /**
     * 查询文件信息列表
     */
    @Operation(summary = "查询文件信息列表", description = "查询文件信息列表")
    @PreAuthorize(hasPermi = "system:fileInfo:list")
    @PostMapping("/back/system/fileInfo/list")
    public CommonResult<List<SystemFileInfoDTO>> list(@RequestBody SystemFileInfoFilterDTO systemFileInfo) {
        List<SystemFileInfoDTO> dtoList = transfer(systemFileInfo);
        return CommonResult.ok(dtoList);
    }

    /**
     * 查询文件信息列表
     */
    @Operation(summary = "查询文件信息列表", description = "查询文件信息列表")
    @PreAuthorize(hasPermi = "system:fileInfo:list")
    @PostMapping("/back/system/fileInfo/list/page")
    public CommonResult<PageListDTO<Object>> listPage(@RequestBody SystemFileInfoFilterDTO systemFileInfo) {
        startPage(systemFileInfo);
        List<SystemFileInfoDTO> dtoList = transfer(systemFileInfo);
        return getDataTable(dtoList);
    }

    private List<SystemFileInfoDTO> transfer(SystemFileInfoFilterDTO systemFileInfo) {
        List<SystemFileInfoDTO> list = systemFileInfoService.selectSystemFileInfoList(systemFileInfo);
        List<SystemFileInfoDTO> dtoList = new ArrayList<>();
        for (SystemFileInfoDTO obj : list) {
            SystemFileInfoDTO dto = new SystemFileInfoDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            if (UploadConstants.OSS.equals(dto.getFileStoreType())) {
                dto.setImageUrl(ossConfig.getNetPath() + "/" + dto.getImageUrl());
            }
            if (UploadConstants.LOCAL.equals(dto.getFileStoreType())) {
                dto.setImageUrl(domain + dto.getImageUrl());
            }
            dto.setModuleName(ProductModuleEnums.geInfoByCode(dto.getModule()));
            dtoList.add(dto);
        }
        return dtoList;
    }

    /**
     * 获取文件信息详细信息
     */
    @Operation(summary = "获取文件信息详细信息", description = "获取文件信息详细信息")
    @PreAuthorize(hasPermi = "system:fileInfo:query")
    @GetMapping(value = "/back/system/fileInfo/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemFileInfoService.selectSystemFileInfoById(id));
    }

    /**
     * 新增文件信息
     */
    @Operation(summary = "新增文件信息", description = "新增文件信息")
    @PreAuthorize(hasPermi = "system:fileInfo:add")
    @Log(title = "文件信息", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/fileInfo/save")
    public CommonResult<Object> add(@RequestBody SystemFileInfoDTO systemFileInfoDTO) {
        SystemFileInfo systemFileInfo = new SystemFileInfo();
        BeanUtils.copyProperties(systemFileInfoDTO, systemFileInfo);
        return toAjax(systemFileInfoService.insertSystemFileInfo(systemFileInfo));
    }

    /**
     * 修改文件信息
     */
    @Operation(summary = "修改文件信息", description = "修改文件信息")
    @PreAuthorize(hasPermi = "system:fileInfo:edit")
    @Log(title = "文件信息", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/fileInfo/edit")
    public CommonResult<Object> edit(@RequestBody SystemFileInfoDTO systemFileInfoDTO) {
        SystemFileInfo systemFileInfo = new SystemFileInfo();
        BeanUtils.copyProperties(systemFileInfoDTO, systemFileInfo);
        return toAjax(systemFileInfoService.updateSystemFileInfo(systemFileInfo));
    }

    /**
     * 删除文件信息
     */
    @Operation(summary = "删除文件信息", description = "删除文件信息")
    @PreAuthorize(hasPermi = "system:fileInfo:remove")
    @Log(title = "文件信息", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/fileInfo/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemFileInfoService.deleteSystemFileInfoByIds(ids));
    }
}
