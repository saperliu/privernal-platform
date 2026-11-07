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

package com.privernal.dynamic.script.controller;

import com.privernal.common.log.enums.OperationType;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.script.domain.DynamicScriptBinding;
import com.privernal.dynamic.script.service.ScriptBindingService;
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

/**
 * 脚本挂点绑定管理端。绑定落独立表，高级设置"校验与算法"面板
 * 整单提交本接口；保存链在 backScript 之前按 execution_order 执行 SAVE_VALIDATE 绑定。
 */
@Tag(name = "脚本挂点绑定")
@Slf4j
@RestController
@RequestMapping("/back/dynamic/script/binding")
public class ScriptBindingController {

    @Autowired
    private ScriptBindingService bindingService;

    @Operation(summary = "按表单查绑定列表")
    @PreAuthorize(hasPermi = "dynamic:script:list")
    @GetMapping("/form/{formId}/list")
    public CommonResult<List<DynamicScriptBinding>> listByForm(@PathVariable("formId") String formId) {
        return CommonResult.ok(bindingService.listByForm(formId));
    }

    @Operation(summary = "批量保存表单的挂点绑定（整表替换）")
    @PreAuthorize(hasPermi = "dynamic:script:edit")
    @PostMapping("/form/{formId}/save")
    public CommonResult<Boolean> saveForForm(@PathVariable("formId") String formId,
                                             @RequestParam(value = "tableCode", required = false) String tableCode,
                                             @RequestBody List<DynamicScriptBinding> bindings) {
        bindingService.saveForForm(formId, tableCode, bindings);
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "启用/停用绑定")
    @PreAuthorize(hasPermi = "dynamic:script:edit")
    @Log(title = "脚本绑定", operationType = OperationType.UPDATE)
    @PostMapping("/{id}/status")
    public CommonResult<Boolean> status(@PathVariable("id") String id,
                                        @RequestParam("enabled") String enabled) {
        bindingService.changeEnabled(id, enabled);
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "删除绑定")
    @PreAuthorize(hasPermi = "dynamic:script:edit")
    @Log(title = "脚本绑定", operationType = OperationType.DELETE)
    @PostMapping("/{id}/delete")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        bindingService.delete(id);
        return CommonResult.ok(Boolean.TRUE);
    }
}
