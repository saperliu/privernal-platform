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

import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.dto.SsoOidcConfigDTO;
import com.privernal.system.filter.SsoOidcConfigFilterDTO;
import com.privernal.system.service.SsoOidcConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SSO（OIDC）渠道配置的管理端接口
 */
@Tag(name = "SSO单点登录渠道配置")
@RestController
@Slf4j
public class SystemSsoConfigController extends BaseController {

    @Autowired
    private SsoOidcConfigService ssoOidcConfigService;

    @Operation(summary = "渠道列表")
    @PreAuthorize(hasPermi = "system:ssoConfig:list")
    @PostMapping("/back/system/sso/config/list")
    public CommonResult<PageListDTO<Object>> configList(@RequestBody SsoOidcConfigFilterDTO filter) {
        startPage(filter);
        List<SsoOidcConfigDTO> list = ssoOidcConfigService.selectList(filter);
        return getDataTable(list);
    }

    @Operation(summary = "渠道详情")
    @PreAuthorize(hasPermi = "system:ssoConfig:query")
    @GetMapping("/back/system/sso/config/{id}")
    public CommonResult<Object> configDetail(@PathVariable("id") String id) {
        return CommonResult.ok(ssoOidcConfigService.selectById(id));
    }

    /**
     * 新建渠道。返回体里的 clientSecret 是唯一的明文回显
     */
    @Operation(summary = "新建渠道")
    @PreAuthorize(hasPermi = "system:ssoConfig:add")
    @Log(title = "SSO渠道", operationType = OperationType.INSERT)
    @PostMapping("/back/system/sso/config/save")
    public CommonResult<Object> addConfig(@RequestBody SsoOidcConfigDTO dto) {
        return CommonResult.ok(ssoOidcConfigService.create(dto));
    }

    @Operation(summary = "修改渠道")
    @PreAuthorize(hasPermi = "system:ssoConfig:edit")
    @Log(title = "SSO渠道", operationType = OperationType.UPDATE)
    @PutMapping("/back/system/sso/config/edit")
    public CommonResult<Object> editConfig(@RequestBody SsoOidcConfigDTO dto) {
        return CommonResult.ok(ssoOidcConfigService.update(dto));
    }

    @Operation(summary = "删除渠道")
    @PreAuthorize(hasPermi = "system:ssoConfig:remove")
    @Log(title = "SSO渠道", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/sso/config/{ids}")
    public CommonResult<Object> removeConfig(@PathVariable("ids") String[] ids) {
        return toAjax(ssoOidcConfigService.deleteByIds(ids));
    }

    @Operation(summary = "发现文档连通性测试并回填端点")
    @PreAuthorize(hasPermi = "system:ssoConfig:test")
    @Log(title = "SSO渠道测试", operationType = OperationType.UPDATE)
    @PutMapping("/back/system/sso/config/test/{id}")
    public CommonResult<Object> testConfig(@PathVariable("id") String id) {
        return CommonResult.ok(ssoOidcConfigService.testConnection(id), "发现文档连通，端点已回填");
    }
}
