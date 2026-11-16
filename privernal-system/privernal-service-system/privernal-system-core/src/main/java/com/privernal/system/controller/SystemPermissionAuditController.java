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

import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.annotation.ServiceRequest;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.dto.PermissionMatrixRowDTO;
import com.privernal.system.dto.RemoteNameResolveDTO;
import com.privernal.system.dto.RemoteNameResolveResultDTO;
import com.privernal.system.filter.PermissionMatrixFilterDTO;
import com.privernal.system.mapper.SystemPermissionAuditMapper;
import com.privernal.system.support.PermissionAuditSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限治理审计Controller（R1-8 权限治理②）
 *
 * ① 权限审计报表的对象名称批量解析（inner，供 message 模块审计查询富化）；
 * ② 人 × 应用 × 数据范围权限台账（导出数据源；非平台管理员强制锁定当前组织）。
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：审计名称解析与权限台账
 */
@Tag(name = "权限治理审计相关接口")
@RestController
public class SystemPermissionAuditController extends BaseController {

    @Autowired
    private SystemPermissionAuditMapper permissionAuditMapper;

    @Autowired
    private PermissionAuditSupport permissionAuditSupport;

    /**
     * 名称批量解析（inner）：用户 id→昵称(loginName)，角色 id→角色名
     */
    @Operation(summary = "名称批量解析", description = "权限审计报表对象名称批量解析（inner）")
    @ServiceRequest
    @PostMapping("/inner/system/permissionAudit/resolveNames")
    public CommonResult<RemoteNameResolveResultDTO> resolveNames(@RequestBody RemoteNameResolveDTO resolveDTO) {
        return CommonResult.ok(permissionAuditSupport.resolveNames(resolveDTO));
    }

    /**
     * 人 × 角色 × 应用 × 权限 × 数据范围 权限台账（导出数据源）
     */
    @Operation(summary = "权限台账", description = "人×应用×数据范围权限台账（仅生效中的授权）")
    @PreAuthorize(hasPermi = "system:permissionAudit:list")
    @PostMapping("/back/system/permissionAudit/matrix")
    public CommonResult<List<PermissionMatrixRowDTO>> matrix(@RequestBody PermissionMatrixFilterDTO filter) {
        if (!SecurityUtils.isSuperPlatformRoleUser()) {
            // 非平台管理员锁定当前组织（防止跨组织拉取台账）
            filter.setOrganizationId(SecurityUtils.getOrganizationId());
        }
        List<PermissionMatrixRowDTO> rows = permissionAuditMapper.selectPermissionMatrix(filter);
        return CommonResult.ok(rows == null ? new ArrayList<>() : rows);
    }
}
