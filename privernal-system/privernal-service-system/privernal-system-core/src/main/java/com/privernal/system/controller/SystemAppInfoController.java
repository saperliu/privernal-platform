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
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemAppInfo;
import com.privernal.system.dto.SystemAppInfoDTO;
import com.privernal.system.filter.SystemAppInfoFilterDTO;
import com.privernal.system.service.ISystemAppInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 应用信息Controller
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用信息的接口
 */
@Tag(name = "应用信息相关接口")
@RestController
@Slf4j
public class SystemAppInfoController extends BaseController {
    @Autowired
    private ISystemAppInfoService systemAppInfoService;

    /**
     * 查询应用信息列表
     */
    @Operation(summary = "查询应用信息列表", description = "查询应用信息列表")
    @PreAuthorize(hasPermi = "system:appInfo:list")
    @PostMapping("/back/system/appInfo/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemAppInfoFilterDTO systemAppInfo) {
        startPage(systemAppInfo);
        List<SystemAppInfoDTO> list = systemAppInfoService.selectSystemAppInfoList(systemAppInfo);
//        List<SystemAppInfoDTO> dtoList = new ArrayList<>();
//        for (SystemAppInfoDTO obj : list) {
//            SystemAppInfoDTO dto = new SystemAppInfoDTO();
//            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
//            dtoList.add(dto);
//        }
        return getDataTable(list);
    }

    /**
     * 获取应用信息详细信息
     */
    @Operation(summary = "获取应用信息详细信息", description = "获取应用信息详细信息")
    @PreAuthorize(hasPermi = "system:appInfo:query")
    @GetMapping(value = "/back/system/appInfo/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemAppInfoService.selectSystemAppInfoDetailDTOById(id));
    }

    /**
     * 新增应用信息
     */
    @Operation(summary = "新增应用信息", description = "新增应用信息")
    @PreAuthorize(hasPermi = "system:appInfo:add")
    @Log(title = "应用信息", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/appInfo/save")
    public CommonResult<Object> add(@RequestBody SystemAppInfoDTO systemAppInfoDTO) {
        return CommonResult.ok(systemAppInfoService.saveSystemAppInfo(systemAppInfoDTO));
    }

    /**
     * 修改应用信息
     */
    @Operation(summary = "修改应用信息", description = "修改应用信息")
    @PreAuthorize(hasPermi = "system:appInfo:edit")
    @Log(title = "应用信息", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/appInfo/edit")
    public CommonResult<Object> edit(@RequestBody SystemAppInfoDTO systemAppInfoDTO) {
        return CommonResult.ok(systemAppInfoService.saveSystemAppInfo(systemAppInfoDTO));
    }

    /**
     * 删除应用信息
     */
    @Operation(summary = "删除应用信息", description = "删除应用信息")
    @PreAuthorize(hasPermi = "system:appInfo:remove")
    @Log(title = "应用信息", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/appInfo/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemAppInfoService.deleteSystemAppInfoByIds(ids));
    }

    /**
     * 查询应用信息列表
     * 每个企业查询自有的应用列表接口。
     * 首页查询该人员的应用列表
     */
    @Operation(summary = "查询应用信息列表", description = "查询应用信息列表")
    @PostMapping("/back/system/appInfo/app/list")
    public CommonResult<List<SystemAppInfoDTO>> indexAppList() {
        String orgId = SecurityUtils.getOrganizationId();
        Boolean oldIgnored = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemAppInfoDTO> list = systemAppInfoService.selectSystemAppInfoListByUserId(orgId, SecurityUtils.getUserId());
            return CommonResult.ok(list);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnored);
            OrganizationContextHolder.clear();
        }
    }

    /**
     * 统计当前组织内非系统应用（type != 'SYSTEM'，即个人/业务创建的应用）数量
     * 首页"应用数量"统计
     */
    @Operation(summary = "获取应用数量", description = "当前组织内个人/业务创建的应用数量（不含系统内置应用）")
    @GetMapping("/back/system/appInfo/num")
    public CommonResult<Object> getOrgAppNum() {
        String orgId = SecurityUtils.getOrganizationId();
        Boolean oldIgnored = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            return CommonResult.ok(systemAppInfoService.selectCustomAppNumByOrganizationId(orgId));
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnored);
            OrganizationContextHolder.clear();
        }
    }

    /**
     * 查询应用信息列表
     * 平台管理员查询某企业的应用列表
     */
    @Operation(summary = "查询应用信息列表", description = "查询应用信息列表")
    @PreAuthorize(hasPermi = "system:appInfo:list")
    @PostMapping("/back/system/appInfo/organization")
    public CommonResult<List<SystemAppInfoDTO>> appAuthList(@RequestBody SystemAppInfoFilterDTO systemAppInfo) {
        Assert.notNull(systemAppInfo.getOrganizationId(), "企业ID不能为空");
        Assert.isTrue(SecurityUtils.isSuperPlatformRoleUser(), "没有权限,请联系管理员");
        Boolean oldIgnored = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemAppInfoDTO> list = systemAppInfoService.selectSystemAppInfoListByUserId(systemAppInfo.getOrganizationId(), systemAppInfo.getUserId());
            return CommonResult.ok(list);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnored);
            OrganizationContextHolder.clear();
        }
    }

}
