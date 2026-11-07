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
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.advanced.domain.DynamicLinkageRule;
import com.privernal.dynamic.advanced.service.LinkageRuleService;
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
 * 联动规则管理端（§7.6）。权限与既有 dynamic:form:* 同族：
 * dynamic:form:linkage（配置管理），日志查看复用同权限。
 */
@Tag(name = "表单联动规则")
@Slf4j
@RestController
@RequestMapping("/back/dynamic/linkage/rule")
public class LinkageRuleController {

    @Autowired
    private LinkageRuleService ruleService;

    @Operation(summary = "按表单查规则列表")
    @PreAuthorize(hasPermi = "dynamic:form:linkage")
    @GetMapping("/form/{formId}")
    public CommonResult<List<DynamicLinkageRule>> listByForm(@PathVariable("formId") String formId) {
        return CommonResult.ok(ruleService.listByFormId(formId));
    }

    @Operation(summary = "规则详情")
    @PreAuthorize(hasPermi = "dynamic:form:linkage")
    @GetMapping("/{id}")
    public CommonResult<DynamicLinkageRule> detail(@PathVariable("id") String id) {
        return CommonResult.ok(ruleService.selectById(id));
    }

    @Operation(summary = "新建规则（含配额与动作校验）")
    @PreAuthorize(hasPermi = "dynamic:form:linkage")
    @Log(title = "联动规则", operationType = OperationType.INSERT)
    @PostMapping("/add")
    public CommonResult<DynamicLinkageRule> add(@RequestBody DynamicLinkageRule rule) {
        return CommonResult.ok(ruleService.insertRule(rule));
    }

    @Operation(summary = "更新规则（rule_code/事件类型不可改）")
    @PreAuthorize(hasPermi = "dynamic:form:linkage")
    @Log(title = "联动规则", operationType = OperationType.UPDATE)
    @PostMapping("/update")
    public CommonResult<DynamicLinkageRule> update(@RequestBody DynamicLinkageRule rule) {
        return CommonResult.ok(ruleService.updateRule(rule));
    }

    @Operation(summary = "启用/停用")
    @PreAuthorize(hasPermi = "dynamic:form:linkage")
    @Log(title = "联动规则", operationType = OperationType.UPDATE)
    @PostMapping("/{id}/status")
    public CommonResult<Boolean> status(@PathVariable("id") String id, @RequestParam("enabled") String enabled) {
        ruleService.updateStatus(id, enabled);
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "删除规则")
    @PreAuthorize(hasPermi = "dynamic:form:linkage")
    @Log(title = "联动规则", operationType = OperationType.DELETE)
    @PostMapping("/{id}/delete")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        ruleService.deleteRule(id);
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "试跑：用一条现有数据模拟事件，返回条件判定与逐动作预览（不执行）")
    @PreAuthorize(hasPermi = "dynamic:form:linkage")
    @PostMapping("/{id}/dryrun")
    public CommonResult<Map<String, Object>> dryRun(@PathVariable("id") String id,
                                                    @RequestParam("dataId") String dataId) {
        return CommonResult.ok(ruleService.dryRun(id, dataId));
    }

    @Operation(summary = "联动执行日志分页")
    @PreAuthorize(hasPermi = "dynamic:form:linkage")
    @PostMapping("/logs")
    public CommonResult<Map<String, Object>> logs(@RequestBody Map<String, Object> filter) {
        return CommonResult.ok(ruleService.selectLogPage(filter));
    }

    @Operation(summary = "TIMER cron 下 n 次触发时刻预览（§7.6 让配置者肉眼确认 cron 语义）")
    @PreAuthorize(hasPermi = "dynamic:form:linkage")
    @GetMapping("/preview-cron")
    public CommonResult<List<String>> previewCron(@RequestParam("cron") String cron,
                                                  @RequestParam(value = "n", defaultValue = "3") int n) {
        try {
            org.springframework.scheduling.support.CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException e) {
            return CommonResult.fail("cron 无法解析：" + e.getMessage());
        }
        java.util.Date current = new java.util.Date();
        List<String> result = new java.util.ArrayList<>();
        for (java.util.Date next : scheduleTaskClient.previewNext(cron.trim(), Math.min(Math.max(n, 1), 10))) {
            // previewNext 基于当前时刻推演，逐次推进展示
            result.add(com.privernal.common.core.utils.DateUtils.parseDateToStr("yyyy-MM-dd HH:mm:ss", next));
            current = next;
        }
        return CommonResult.ok(result);
    }

    @Autowired
    private com.privernal.job.api.ScheduleTaskClient scheduleTaskClient;
}
