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

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.advanced.engine.AdvancedKillSwitch;
import com.privernal.dynamic.advanced.service.IoDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 表单数据导入导出运行时端点（T1）。
 * 权限：dynamic:data:import / dynamic:data:export（运行时动作，与定义级 dynamic:form:import/export 区分）。
 * 审计：导出/导入/模板下载统一 @Log 操作日志通道（§4.2 导出审计、§8.3 批量出站留痕）。
 */
@Tag(name = "表单数据导入导出")
@Slf4j
@RestController
@RequestMapping("/back/dynamic/io")
public class IoDataController {

    @Autowired
    private IoDataService ioDataService;

    @Autowired
    private AdvancedKillSwitch killSwitch;

    @Operation(summary = "下载导入模板（两 Sheet：数据+说明）")
    @PreAuthorize(hasPermi = "dynamic:data:import")
    @Log(title = "动态表数据")
    @GetMapping("/template/{formId}")
    public void template(@PathVariable("formId") String formId, HttpServletResponse response) throws Exception {
        if (!killSwitch.ioEnabled()) {
            response.sendError(403, "导入导出能力已被平台关停");
            return;
        }
        byte[] bytes = ioDataService.buildTemplate(formId);
        writeExcel(response, bytes, "import-template-" + formId + ".xlsx");
    }

    @Operation(summary = "上传 Excel 发起异步导入，返回 taskId")
    @PreAuthorize(hasPermi = "dynamic:data:import")
    @Log(title = "动态表数据")
    @PostMapping("/import/{formId}")
    public CommonResult<Map<String, Object>> upload(@PathVariable("formId") String formId,
                                                    @RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return CommonResult.fail("请选择要导入的 Excel 文件");
        }
        if (file.getSize() > 10 * 1024 * 1024L) {
            return CommonResult.fail("文件超过 10MB 上限");
        }
        return CommonResult.ok(ioDataService.startImport(formId, file.getBytes()));
    }

    @Operation(summary = "导入进度轮询")
    @PreAuthorize(hasPermi = "dynamic:data:import")
    @GetMapping("/import/progress/{taskId}")
    public CommonResult<Map<String, Object>> progress(@PathVariable("taskId") String taskId) {
        return CommonResult.ok(ioDataService.progress(taskId));
    }

    @Operation(summary = "下载失败行报告（可修改后重新导入）")
    @PreAuthorize(hasPermi = "dynamic:data:import")
    @GetMapping("/import/report/{taskId}")
    public void report(@PathVariable("taskId") String taskId, HttpServletResponse response) throws Exception {
        byte[] bytes = ioDataService.buildFailureReport(taskId);
        writeExcel(response, bytes, "import-report-" + taskId + ".xlsx");
    }

    @Operation(summary = "导出数据 Excel（敏感列脱敏/剔除，选项回译，@Log 审计）")
    @PreAuthorize(hasPermi = "dynamic:data:export")
    @Log(title = "动态表数据")
    @PostMapping("/export/{formId}")
    public void export(@PathVariable("formId") String formId,
                       @RequestBody(required = false) Map<String, Object> query,
                       HttpServletResponse response) throws Exception {
        byte[] bytes = ioDataService.export(formId, query);
        writeExcel(response, bytes, "export-" + formId + "-" + System.currentTimeMillis() + ".xlsx");
    }

    private void writeExcel(HttpServletResponse response, byte[] bytes, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
