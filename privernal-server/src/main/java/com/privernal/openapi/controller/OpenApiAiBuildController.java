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

package com.privernal.openapi.controller;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.aspect.DataScopeHelper;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.dbs.service.impl.DynamicFormValidateService;
import com.privernal.dynamic.dto.DynamicFormDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 开发一档通道：/openapi/ai/build/** 只挂"读 + 纯校验"端点。
 * <p>
 * 边界口径：二档（发布/回滚/编辑）与三档（删除/权限侧）在<b>本通道上不存在</b>——不是被拦，
 * 是根本没有端点（构造性不可达）；过滤器再加交叉强制（ai:build 凭证 ↔ 本通道互斥绑定）。
 * 无登录态线程：组织上下文由 {@code OpenApiAuthFilter} 从凭证注入，数据范围切面按
 * 联动引擎先例 mergeIgnoreStrategy 放行（凭证即授权主体）。
 * <p>
 * 尚未上通道的能力（待机读身份能力就绪后扩展）：草稿创建、脚本资产试运行——
 * 当前一档通道先交付读 + 纯校验，通道本身与 scope 强制已就位。
 */
@Tag(name = "AI 开发一档通道")
@Slf4j
@RestController
@RequestMapping("/openapi/ai/build")
@RequiredArgsConstructor
public class OpenApiAiBuildController {

    @Autowired
    private DynamicFormValidateService formValidateService;

    @Autowired
    private com.privernal.dynamic.dbs.service.IDynamicFormService dynamicFormService;

    @Operation(summary = "表单定义纯校验（B1 同款，零副作用）")
    @PostMapping("/form/validate")
    public CommonResult<List<Map<String, Object>>> validate(@RequestBody DynamicFormDTO form) {
        return CommonResult.ok(withScopeIgnore(() -> formValidateService.validateDefinition(form)));
    }

    @Operation(summary = "按表单编码只读回读（草稿/定义核对）")
    @GetMapping("/form/code/{code}")
    public CommonResult<Object> formByCode(@PathVariable("code") String code) {
        if (StringUtils.isEmpty(code)) {
            return CommonResult.fail("code 不能为空");
        }
        return CommonResult.ok(withScopeIgnore(() ->
                dynamicFormService.selectDynamicFormByTableCode(code)));
    }

    private <T> T withScopeIgnore(java.util.function.Supplier<T> call) {
        Boolean prevOrg = DataScopeHelper.getIgnoreOrganization();
        Boolean prevDp = DataScopeHelper.getIgnoreDataPermission();
        DataScopeHelper.mergeIgnoreStrategy(true, true);
        try {
            return call.get();
        } finally {
            DataScopeHelper.mergeIgnoreStrategy(prevOrg, prevDp);
        }
    }
}
