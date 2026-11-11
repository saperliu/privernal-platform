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

package com.privernal.flow.controller;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.flow.domain.FlowUserDelegate;
import com.privernal.flow.service.IFlowUserDelegateService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 流程代理规则（R2-17 代理与离职移交）：user_id 的待办生成时代理人一并加入办理人。
 *
 * @author saperliu
 */
@RestController
@RequestMapping("/back/flow/delegate")
public class FlowUserDelegateController {

    @Autowired
    private IFlowUserDelegateService delegateService;

    /**
     * 代理规则列表（userId 空查当前组织全部，非空限定被代理人）
     */
    @Operation(summary = "代理规则列表")
    @PreAuthorize(hasPermi = "flow:task:list")
    @GetMapping("/list")
    public CommonResult<List<FlowUserDelegate>> list(@RequestParam(value = "userId", required = false) String userId) {
        return CommonResult.ok(delegateService.list(StringUtils.isEmpty(userId)
                ? com.privernal.common.security.service.SecurityUtils.getOrganizationId() : null, userId));
    }

    /**
     * 新增代理规则
     */
    @Operation(summary = "新增代理规则")
    @PreAuthorize(hasPermi = "flow:task:delegate")
    @PostMapping("/save")
    public CommonResult<FlowUserDelegate> save(@RequestBody FlowUserDelegate delegate) {
        return CommonResult.ok(delegateService.save(delegate));
    }

    /**
     * 修改代理规则
     */
    @Operation(summary = "修改代理规则")
    @PreAuthorize(hasPermi = "flow:task:delegate")
    @PutMapping("/edit")
    public CommonResult<FlowUserDelegate> edit(@RequestBody FlowUserDelegate delegate) {
        return CommonResult.ok(delegateService.edit(delegate));
    }

    /**
     * 删除代理规则（按 id 批量）
     */
    @Operation(summary = "删除代理规则")
    @PreAuthorize(hasPermi = "flow:task:delegate")
    @DeleteMapping("/{ids}")
    public CommonResult<Integer> remove(@PathVariable("ids") String ids) {
        return CommonResult.ok(delegateService.remove(Arrays.asList(ids.split(","))));
    }
}
