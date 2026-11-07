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

import com.privernal.common.log.enums.OperationType;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.domain.SystemAiConfig;
import com.privernal.system.service.ISystemAiConfigService;
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

import java.util.Map;

/**
 * AI 模型配置管理端（公网模型口径）。
 * <p>
 * 数据流向明示（v1.4）：所有响应携带 {@link SystemAiConfig#DATA_FLOW_NOTICE}——
 * 公网模型=需求文本与业务数据会传输至所配服务商；内网模型=数据不出内网；配置即授权。
 * apiKey 只存密文，查询只回掩码。
 */
@Tag(name = "AI 模型配置")
@Slf4j
@RestController
@RequestMapping("/back/system/ai")
public class SystemAiConfigController extends BaseController {

    @Autowired
    private ISystemAiConfigService aiConfigService;

    @Operation(summary = "当前组织模型配置（apiKey 只回掩码；附数据流向明示）")
    @PreAuthorize(hasPermi = "system:aiConfig:query")
    @GetMapping("/ai/config/current")
    public CommonResult<Map<String, Object>> current() {
        return CommonResult.ok(aiConfigService.currentWithNotice());
    }

    @Operation(summary = "保存模型配置（upsert；apiKey 非空重新加密、空保持不变）")
    @PreAuthorize(hasPermi = "system:aiConfig:save")
    @Log(title = "AI 模型配置", operationType = OperationType.INSERT)
    @PostMapping("/ai/config/save")
    public CommonResult<Map<String, Object>> save(@RequestBody Map<String, Object> payload) {
        SystemAiConfig patch = new SystemAiConfig();
        patch.setProvider(str(payload.get("provider")));
        patch.setBaseUrl(str(payload.get("baseUrl")));
        patch.setModel(str(payload.get("model")));
        Object temperature = payload.get("temperature");
        if (temperature != null && !String.valueOf(temperature).isEmpty()) {
            patch.setTemperature(new java.math.BigDecimal(String.valueOf(temperature)));
        }
        Object tokenLimit = payload.get("sessionTokenLimit");
        if (tokenLimit != null && !String.valueOf(tokenLimit).isEmpty()) {
            patch.setSessionTokenLimit(Integer.valueOf(String.valueOf(tokenLimit)));
        }
        patch.setIntranetFlag(str(payload.get("intranetFlag")));
        patch.setEnabled(str(payload.get("enabled")));
        aiConfigService.save(patch, str(payload.get("apiKey")));
        return CommonResult.ok(aiConfigService.currentWithNotice());
    }

    @Operation(summary = "启停本组织 AI 能力")
    @PreAuthorize(hasPermi = "system:aiConfig:save")
    @Log(title = "AI 模型配置", operationType = OperationType.UPDATE)
    @PostMapping("/ai/config/enable")
    public CommonResult<Boolean> enable(@RequestParam("enabled") String enabled) {
        aiConfigService.changeEnabled(enabled);
        return CommonResult.ok(Boolean.TRUE);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
