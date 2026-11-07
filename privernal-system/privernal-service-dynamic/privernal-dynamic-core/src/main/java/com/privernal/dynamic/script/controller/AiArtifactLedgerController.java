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
import com.privernal.dynamic.script.domain.AiArtifactLedger;
import com.privernal.dynamic.script.service.AiArtifactLedgerService;
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
 * AI 生成物台账管理端。平台尚无 AI 运行时，
 * sessionId 暂取来源标识（人工=manual，编排层接入后=会话 id）。
 */
@Tag(name = "AI 生成物台账")
@Slf4j
@RestController
@RequestMapping("/back/dynamic/ai/artifact")
public class AiArtifactLedgerController {

    @Autowired
    private AiArtifactLedgerService ledgerService;

    @Operation(summary = "登记生成物（幂等）")
    @PreAuthorize(hasPermi = "dynamic:form:edit")
    @Log(title = "AI 产物台账", operationType = OperationType.INSERT)
    @PostMapping("/record")
    public CommonResult<AiArtifactLedger> record(@RequestBody Map<String, String> payload) {
        return CommonResult.ok(ledgerService.record(
                payload.get("sessionId"), payload.get("objectType"),
                payload.get("objectId"), payload.get("code")));
    }

    @Operation(summary = "台账列表（按会话/类型/撤销态过滤）")
    @PreAuthorize(hasPermi = "dynamic:form:list")
    @GetMapping("/list")
    public CommonResult<List<AiArtifactLedger>> list(
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "objectType", required = false) String objectType,
            @RequestParam(value = "rolledBack", required = false) String rolledBack) {
        return CommonResult.ok(ledgerService.list(sessionId, objectType, rolledBack));
    }

    @Operation(summary = "撤销生成物（按对象类型分派撤销语义）")
    @PreAuthorize(hasPermi = "dynamic:form:edit")
    @Log(title = "AI 产物台账", operationType = OperationType.UPDATE)
    @PostMapping("/{id}/rollback")
    public CommonResult<String> rollback(@PathVariable("id") String id) {
        return CommonResult.ok(ledgerService.rollback(id));
    }
}
