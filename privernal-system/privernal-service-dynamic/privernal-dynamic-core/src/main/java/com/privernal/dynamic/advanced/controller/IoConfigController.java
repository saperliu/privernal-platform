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

package com.privernal.dynamic.advanced.controller;

import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.advanced.domain.DynamicFormIoConfig;
import com.privernal.dynamic.advanced.service.IoConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导入导出配置管理端（§4.1/§4.3）。配置权限 dynamic:form:io:config；
 * 运行时数据导入/导出动作（dynamic:data:import/export）本体为 P1。
 */
@Tag(name = "表单导入导出配置")
@Slf4j
@RestController
@RequestMapping("/back/dynamic/io/config")
public class IoConfigController {

    @Autowired
    private IoConfigService ioConfigService;

    @Operation(summary = "按表单读配置（未配置返回默认值）")
    @PreAuthorize(hasPermi = "dynamic:form:io:config")
    @GetMapping
    public CommonResult<DynamicFormIoConfig> get(@RequestParam("formId") String formId) {
        return CommonResult.ok(ioConfigService.getByFormId(formId));
    }

    @Operation(summary = "保存配置（一表单一行，实时生效）")
    @PreAuthorize(hasPermi = "dynamic:form:io:config")
    @PostMapping
    public CommonResult<DynamicFormIoConfig> save(@RequestBody DynamicFormIoConfig config) {
        return CommonResult.ok(ioConfigService.save(config));
    }
}
