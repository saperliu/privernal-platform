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

package com.privernal.dynamic.report.controller;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.report.domain.DynamicReport;
import com.privernal.dynamic.report.service.IDynamicReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报表图表 Controller
 *
 * @author saperliu
 */
@Tag(name = "报表图表相关接口")
@RestController
@Slf4j
@RequestMapping("/back/report/report")
public class DynamicReportController extends BaseController {

    @Autowired
    private IDynamicReportService reportService;

    @Autowired
    private com.privernal.dynamic.script.service.AiArtifactLedgerService artifactLedgerService;

    /**
     * 保存图表
     */
    @Operation(summary = "保存图表")
    @PreAuthorize(hasPermi = "report:report:add")
    @Log(title = "报表图表", operationType = OperationType.UPDATE)
    @PostMapping("/save")
    public CommonResult<Object> save(@RequestBody DynamicReport report) {
        DynamicReport saved = reportService.saveDynamicReport(report);
        // B3 生成物台账：图表创建/编辑即登记（编排层接入后 sessionId=会话 id）
        try {
            artifactLedgerService.record(null, "REPORT", saved.getReportCode(), saved.getReportCode());
        } catch (Exception e) {
            log.warn("生成物台账登记失败 report={}", saved.getReportCode(), e);
        }
        return CommonResult.ok(saved);
    }

    /**
     * 图表列表（按应用）
     */
    @Operation(summary = "图表列表")
    @PreAuthorize(hasPermi = "report:report:list")
    @GetMapping("/list")
    public CommonResult<Object> list(@RequestParam("appId") String appId) {
        List<DynamicReport> list = reportService.listByApp(appId);
        return CommonResult.ok(list);
    }

    /**
     * 图表详情
     */
    @Operation(summary = "图表详情")
    @PreAuthorize(hasPermi = "report:report:query")
    @GetMapping("/{reportCode}")
    public CommonResult<Object> detail(@PathVariable("reportCode") String reportCode) {
        return CommonResult.ok(reportService.findByCode(reportCode));
    }

    /**
     * 删除图表
     */
    @Operation(summary = "删除图表")
    @PreAuthorize(hasPermi = "report:report:remove")
    @Log(title = "报表图表", operationType = OperationType.DELETE)
    @DeleteMapping("/{reportCode}")
    public CommonResult<Object> remove(@PathVariable("reportCode") String reportCode) {
        if (StringUtils.isBlank(reportCode)) {
            return CommonResult.fail("图表编码不能为空");
        }
        return toAjax(reportService.deleteByCode(reportCode));
    }
}
