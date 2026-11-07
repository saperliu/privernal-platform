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
import com.privernal.dynamic.script.domain.DynamicScriptAsset;
import com.privernal.dynamic.script.domain.DynamicScriptAssetLog;
import com.privernal.dynamic.script.domain.DynamicScriptBinding;
import com.privernal.dynamic.script.domain.DynamicScriptExecLog;
import com.privernal.dynamic.script.service.ScriptAssetService;
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
 * 脚本资产管理端。权限码 dynamic:script:*（种子见 upgrade_script_asset.sql，
 * 仅自动授组织管理员，交付报告写授权缺口）。
 */
@Tag(name = "脚本资产")
@Slf4j
@RestController
@RequestMapping("/back/dynamic/script/asset")
public class ScriptAssetController {

    @Autowired
    private ScriptAssetService assetService;

    @Operation(summary = "分页查询脚本资产")
    @PreAuthorize(hasPermi = "dynamic:script:list")
    @GetMapping("/list")
    public CommonResult<?> list(@RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                @RequestParam(value = "pageSize", defaultValue = "20") long pageSize,
                                @RequestParam(value = "keyword", required = false) String keyword,
                                @RequestParam(value = "category", required = false) String category,
                                @RequestParam(value = "status", required = false) String status,
                                @RequestParam(value = "appId", required = false) String appId) {
        return CommonResult.ok(assetService.page(pageNum, pageSize, keyword, category, status, appId));
    }

    @Operation(summary = "资产详情")
    @PreAuthorize(hasPermi = "dynamic:script:query")
    @GetMapping("/{id}")
    public CommonResult<DynamicScriptAsset> detail(@PathVariable("id") String id) {
        return CommonResult.ok(assetService.requireAsset(id));
    }

    @Operation(summary = "新建脚本资产（沙箱校验，落草稿 v1）")
    @PreAuthorize(hasPermi = "dynamic:script:add")
    @Log(title = "脚本资产", operationType = OperationType.INSERT)
    @PostMapping("/add")
    public CommonResult<DynamicScriptAsset> add(@RequestBody DynamicScriptAsset asset) {
        return CommonResult.ok(assetService.create(asset));
    }

    @Operation(summary = "编辑脚本资产（已发布改正文/签名自动递增版本回草稿）")
    @PreAuthorize(hasPermi = "dynamic:script:edit")
    @Log(title = "脚本资产", operationType = OperationType.UPDATE)
    @PostMapping("/edit")
    public CommonResult<DynamicScriptAsset> edit(@RequestBody DynamicScriptAsset asset) {
        return CommonResult.ok(assetService.update(asset));
    }

    @Operation(summary = "发布（要求该版本试运行成功过一次）")
    @PreAuthorize(hasPermi = "dynamic:script:publish")
    @Log(title = "脚本资产", operationType = OperationType.UPDATE)
    @PostMapping("/{id}/publish")
    public CommonResult<Boolean> publish(@PathVariable("id") String id) {
        assetService.publish(id);
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "停用/重新启用（status=DISABLED/PUBLISHED）")
    @PreAuthorize(hasPermi = "dynamic:script:publish")
    @Log(title = "脚本资产", operationType = OperationType.UPDATE)
    @PostMapping("/{id}/status")
    public CommonResult<Boolean> status(@PathVariable("id") String id,
                                        @RequestParam("status") String status) {
        assetService.changeStatus(id, status);
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "试运行（发布前必须成功一次）")
    @PreAuthorize(hasPermi = "dynamic:script:run")
    @Log(title = "脚本资产", operationType = OperationType.OTHER)
    @PostMapping("/{id}/run")
    public CommonResult<Map<String, Object>> trial(@PathVariable("id") String id,
                                                   @RequestBody(required = false) Map<String, Object> params) {
        return CommonResult.ok(assetService.trial(id, params));
    }

    @Operation(summary = "按编码直跑当前发布版（backScript 迁移入口）")
    @PreAuthorize(hasPermi = "dynamic:script:run")
    @Log(title = "脚本资产", operationType = OperationType.OTHER)
    @PostMapping("/run")
    public CommonResult<Object> runByCode(@RequestBody Map<String, Object> payload) {
        Object code = payload.get("scriptCode");
        if (code == null || String.valueOf(code).isEmpty()) {
            return CommonResult.fail("scriptCode 不能为空");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) payload.get("params");
        return CommonResult.ok(assetService.runByCode(String.valueOf(code), params));
    }

    @Operation(summary = "删除（被绑定引用时拒绝）")
    @PreAuthorize(hasPermi = "dynamic:script:remove")
    @Log(title = "脚本资产", operationType = OperationType.DELETE)
    @PostMapping("/{id}/delete")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        assetService.delete(id);
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "引用影响面：被哪些表单/挂点绑定")
    @PreAuthorize(hasPermi = "dynamic:script:query")
    @GetMapping("/{id}/references")
    public CommonResult<List<DynamicScriptBinding>> references(@PathVariable("id") String id) {
        return CommonResult.ok(assetService.references(id));
    }

    @Operation(summary = "执行日志（最近在前）")
    @PreAuthorize(hasPermi = "dynamic:script:query")
    @GetMapping("/{id}/exec-logs")
    public CommonResult<List<DynamicScriptExecLog>> execLogs(
            @PathVariable("id") String id,
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize) {
        return CommonResult.ok(assetService.execLogs(id, pageNum, pageSize));
    }

    @Operation(summary = "版本历史（发布/停用快照）")
    @PreAuthorize(hasPermi = "dynamic:script:query")
    @GetMapping("/{id}/versions")
    public CommonResult<List<DynamicScriptAssetLog>> versions(@PathVariable("id") String id) {
        return CommonResult.ok(assetService.versionHistory(id));
    }
}
