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
import com.privernal.system.dto.OpenApiCallLogDTO;
import com.privernal.system.dto.OpenApiCallStatDTO;
import com.privernal.system.dto.OpenApiCredentialDTO;
import com.privernal.system.filter.OpenApiCallLogFilterDTO;
import com.privernal.system.filter.OpenApiCredentialFilterDTO;
import com.privernal.system.service.OpenApiCallLogService;
import com.privernal.system.service.OpenApiCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OpenAPI 应用凭证与调用统计的管理端接口
 */
@Tag(name = "OpenAPI凭证与调用统计")
@RestController
@Slf4j
public class SystemOpenapiController extends BaseController {

    @Autowired
    private OpenApiCredentialService credentialService;

    @Autowired
    private OpenApiCallLogService callLogService;

    @Operation(summary = "查询凭证列表")
    @PreAuthorize(hasPermi = "system:openapi:list")
    @PostMapping("/back/system/openapi/credential/list")
    public CommonResult<PageListDTO<Object>> credentialList(@RequestBody OpenApiCredentialFilterDTO filter) {
        startPage(filter);
        List<OpenApiCredentialDTO> list = credentialService.selectSystemOpenapiCredentialList(filter);
        return getDataTable(list);
    }

    @Operation(summary = "凭证详情")
    @PreAuthorize(hasPermi = "system:openapi:query")
    @GetMapping("/back/system/openapi/credential/{id}")
    public CommonResult<Object> credentialDetail(@PathVariable("id") String id) {
        return CommonResult.ok(credentialService.selectById(id));
    }

    /**
     * 新建凭证。返回体里的 secret 是唯一的明文出口，前端必须提示用户立即复制
     */
    @Operation(summary = "新建凭证")
    @PreAuthorize(hasPermi = "system:openapi:add")
    @Log(title = "OpenAPI凭证", operationType = OperationType.INSERT)
    @PostMapping("/back/system/openapi/credential/save")
    public CommonResult<Object> addCredential(@RequestBody OpenApiCredentialDTO dto) {
        return CommonResult.ok(credentialService.createCredential(dto));
    }

    @Operation(summary = "修改凭证")
    @PreAuthorize(hasPermi = "system:openapi:edit")
    @Log(title = "OpenAPI凭证", operationType = OperationType.UPDATE)
    @PutMapping("/back/system/openapi/credential/edit")
    public CommonResult<Object> editCredential(@RequestBody OpenApiCredentialDTO dto) {
        return CommonResult.ok(credentialService.updateCredential(dto));
    }

    @Operation(summary = "删除凭证")
    @PreAuthorize(hasPermi = "system:openapi:remove")
    @Log(title = "OpenAPI凭证", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/openapi/credential/{ids}")
    public CommonResult<Object> removeCredential(@PathVariable("ids") String[] ids) {
        return toAjax(credentialService.deleteByIds(ids));
    }

    @Operation(summary = "轮换凭证密钥")
    @PreAuthorize(hasPermi = "system:openapi:rotate")
    @Log(title = "OpenAPI凭证轮换", operationType = OperationType.UPDATE)
    @PutMapping("/back/system/openapi/credential/rotate/{id}")
    public CommonResult<Object> rotateCredential(@PathVariable("id") String id) {
        return CommonResult.ok(credentialService.rotateSecret(id));
    }

    @Operation(summary = "调用流水列表")
    @PreAuthorize(hasPermi = "system:openapi:callLog")
    @PostMapping("/back/system/openapi/callLog/list")
    public CommonResult<PageListDTO<Object>> callLogList(@RequestBody OpenApiCallLogFilterDTO filter) {
        startPage(filter);
        List<OpenApiCallLogDTO> list = callLogService.selectList(filter);
        return getDataTable(list);
    }

    @Operation(summary = "调用统计")
    @PreAuthorize(hasPermi = "system:openapi:callLog")
    @PostMapping("/back/system/openapi/callLog/stat")
    public CommonResult<Object> callLogStat(@RequestBody OpenApiCallLogFilterDTO filter,
                                           @RequestParam(value = "groupByDay", required = false, defaultValue = "true") boolean groupByDay) {
        List<OpenApiCallStatDTO> stat = callLogService.stat(filter, groupByDay);
        return CommonResult.ok(stat);
    }
}
