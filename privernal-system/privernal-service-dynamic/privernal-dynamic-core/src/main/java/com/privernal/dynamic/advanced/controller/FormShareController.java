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

import com.privernal.common.log.enums.OperationType;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.advanced.domain.DynamicFormShare;
import com.privernal.dynamic.advanced.service.FormShareService;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.service.IDynamicFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 表单外链管理端（§5.4）：创建（返回一次性明文 token）、启停、轮换、列表、删除。
 */
@Tag(name = "表单外链")
@Slf4j
@RestController
@RequestMapping("/back/dynamic/share")
public class FormShareController {

    @Autowired
    private FormShareService shareService;

    @Autowired
    private IDynamicFormService dynamicFormService;

    @Operation(summary = "创建外链，返回一次性明文 token")
    @PreAuthorize(hasPermi = "dynamic:form:share")
    @Log(title = "表单外链", operationType = OperationType.INSERT)
    @PostMapping("/add")
    public CommonResult<Map<String, Object>> add(@RequestBody DynamicFormShare share) {
        if (!shareService.shareEnabled()) {
            return CommonResult.fail("外链能力已被平台关停（kill switch）");
        }
        DynamicForm form = dynamicFormService.selectDynamicFormById(share.getFormId());
        if (form == null) {
            throw new ServiceException("表单不存在：" + share.getFormId());
        }
        return CommonResult.ok(shareService.createShare(share, form));
    }

    @Operation(summary = "按表单查外链列表")
    @PreAuthorize(hasPermi = "dynamic:form:share")
    @GetMapping("/form/{formId}/list")
    public CommonResult<List<DynamicFormShare>> listByForm(@PathVariable("formId") String formId) {
        return CommonResult.ok(shareService.listByFormId(formId));
    }

    @Operation(summary = "轮换 token（旧链接立即失效），返回新的一次性明文 token")
    @PreAuthorize(hasPermi = "dynamic:form:share")
    @Log(title = "表单外链", operationType = OperationType.UPDATE)
    @PostMapping("/{id}/rotate")
    public CommonResult<Map<String, Object>> rotate(@PathVariable("id") String id) {
        return CommonResult.ok(shareService.rotateToken(id));
    }

    @Operation(summary = "启用/停用（停用后链接立即 404）")
    @PreAuthorize(hasPermi = "dynamic:form:share")
    @Log(title = "表单外链", operationType = OperationType.UPDATE)
    @PostMapping("/{id}/status")
    public CommonResult<Boolean> status(@PathVariable("id") String id, @RequestParam("enabled") String enabled) {
        shareService.updateStatus(id, enabled);
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "修改外链配置（不动 token 与已发链接）")
    @PreAuthorize(hasPermi = "dynamic:form:share")
    @Log(title = "表单外链", operationType = OperationType.UPDATE)
    @PostMapping("/{id}/update")
    public CommonResult<DynamicFormShare> update(@PathVariable("id") String id,
                                                 @RequestBody DynamicFormShare patch) {
        patch.setId(id);
        return CommonResult.ok(shareService.updateShare(id, patch));
    }

    @Operation(summary = "取链回显（开关默认关，dynamic.share.reveal-enabled）")
    @PreAuthorize(hasPermi = "dynamic:form:share")
    @GetMapping("/{id}/link")
    public CommonResult<Map<String, Object>> link(@PathVariable("id") String id,
                                                  jakarta.servlet.http.HttpServletRequest request) {
        String operatorId;
        try {
            operatorId = com.privernal.common.security.service.SecurityUtils.getUserId();
        } catch (Exception e) {
            operatorId = null;
        }
        return CommonResult.ok(shareService.revealLink(id, operatorId,
                clientIp(request), request.getHeader("User-Agent")));
    }

    private static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Operation(summary = "删除外链")
    @PreAuthorize(hasPermi = "dynamic:form:share")
    @Log(title = "表单外链", operationType = OperationType.DELETE)
    @PostMapping("/{id}/delete")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        shareService.deleteShare(id);
        return CommonResult.ok(Boolean.TRUE);
    }
}
