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

import com.privernal.common.log.annotation.Log;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.advanced.domain.DynamicDocTemplate;
import com.privernal.dynamic.advanced.service.DocPrintDataService;
import com.privernal.dynamic.advanced.service.DocTemplateService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
 * 单据打印模板管理端（plan-sheet-print §3.3/§3.6，M1 模板 CRUD + M3 运行态取数通道）。
 * 路径统一落 /back/print/*（与 /back/report/* 同层级）。
 * 运行态权限：打印动作沿用 dynamic:data:print（避免二次改动所有角色授权），
 * 服务端另按表功能权限 dynamic:{tableCode}:list 收口（DocPrintDataService）。
 * 留痕：/print/data 出站动作必须 @Log（§1.2 缺陷 3，记录 templateCode/行数/操作人）。
 */
@Tag(name = "单据打印模板")
@Slf4j
@RestController
@RequestMapping("/back/print")
public class DocTemplateController {

    @Autowired
    private DocTemplateService docTemplateService;

    @Autowired
    private DocPrintDataService docPrintDataService;

    @Autowired
    private com.privernal.dynamic.advanced.service.DocPrintPdfService docPrintPdfService;

    @Operation(summary = "渲染后 HTML 出 PDF 归档（openhtmltopdf + 中文字体 + 真页码）")
    @PreAuthorize(hasPermi = "dynamic:data:print")
    @Log(title = "单据打印PDF", isSaveRequestData = false)
    @PostMapping("/pdf/{templateCode}")
    public void printPdf(@PathVariable("templateCode") String templateCode,
                         @RequestParam(value = "docs", required = false) Integer docs,
                         @RequestParam(value = "detailRows", required = false) Integer detailRows,
                         @RequestBody Map<String, Object> body, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        // 留痕口径（§3.6）：模板编码与份数/明细行数都走查询串，切面记录的 operation_url
        // （requestURI 含 query）天然带上；html 体量大（可达数 MB），记进 operation_param
        // 只会被 900 字符截断并淹没有效信息，故 isSaveRequestData=false
        if (templateCode == null || !templateCode.matches("[A-Za-z0-9_-]{1,64}")) {
            response.sendError(400, "templateCode 非法");
            return;
        }
        String html = (String) body.get("html");
        if (html == null || html.isBlank()) {
            response.sendError(400, "html 不能为空");
            return;
        }
        if (html.length() > 20_000_000) {
            response.sendError(413, "打印内容过大，无法生成 PDF");
            return;
        }
        byte[] pdf = docPrintPdfService.renderPdf(html);
        log.info("单据打印 PDF templateCode {} 份数 {} 明细行 {} 操作人 {} 字节 {}",
                templateCode, docs, detailRows, com.privernal.common.security.service.SecurityUtils.getLoginName(), pdf.length);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=doc-print-" + templateCode + ".pdf");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }

    @Operation(summary = "按模板渲染取数（主表+子表、可读值、服务端脱敏、行级范围收口、@Log 留痕）")
    @PreAuthorize(hasPermi = "dynamic:data:print")
    @Log(title = "单据打印")
    @PostMapping("/data")
    public CommonResult<Map<String, Object>> printData(@RequestBody Map<String, Object> body) {
        String templateCode = (String) body.get("templateCode");
        @SuppressWarnings("unchecked")
        List<String> dataIds = (List<String>) body.get("dataIds");
        // Jackson 把嵌套结构反序列化成 List/Map，需经 JSON 中转成 fastjson2 的 JSONArray
        Object rawFilter = body.get("filter");
        JSONArray filters = rawFilter == null ? null : JSON.parseArray(JSON.toJSONString(rawFilter));
        return CommonResult.ok(docPrintDataService.printData(templateCode, dataIds, filters));
    }

    @Operation(summary = "模板可用字段树 [{field,label,dataType,kind:main|detail}]")
    @PreAuthorize(hasPermi = "dynamic:docTemplate:list")
    @PostMapping("/fields")
    public CommonResult<List<Map<String, Object>>> fields(@RequestBody Map<String, Object> body) {
        String templateCode = (String) body.get("templateCode");
        String formId = (String) body.get("formId");
        DynamicDocTemplate template = null;
        if (templateCode != null) {
            template = docTemplateService.getByTemplateCode(templateCode);
        } else if (formId != null) {
            // 未保存过模板时按表单出字段树（设计态侧栏用）
            template = new DynamicDocTemplate();
            template.setFormId(formId);
        }
        if (template == null) {
            return CommonResult.fail("templateCode 与 formId 至少给一个");
        }
        return CommonResult.ok(docPrintDataService.fieldTree(template));
    }

    @Operation(summary = "按表单查模板列表（管理态，含停用）")
    @PreAuthorize(hasPermi = "dynamic:docTemplate:list")
    @Log(title = "单据打印模板")
    @GetMapping("/template/form/{formId}")
    public CommonResult<List<DynamicDocTemplate>> listByForm(@PathVariable("formId") String formId) {
        return CommonResult.ok(docTemplateService.listByFormId(formId));
    }

    @Operation(summary = "模板详情（编辑器载入）")
    @PreAuthorize(hasPermi = "dynamic:docTemplate:list")
    @GetMapping("/template/{id}")
    public CommonResult<DynamicDocTemplate> detail(@PathVariable("id") String id) {
        return CommonResult.ok(docTemplateService.getById(id));
    }

    @Operation(summary = "新建模板（保存期 binding 一致性校验，失配拦截）")
    @PreAuthorize(hasPermi = "dynamic:docTemplate:add")
    @Log(title = "单据打印模板")
    @PostMapping("/template/add")
    public CommonResult<DynamicDocTemplate> add(@RequestBody DynamicDocTemplate template) {
        return CommonResult.ok(docTemplateService.insertTemplate(template));
    }

    @Operation(summary = "更新模板（templateCode/formId 锚点不可改）")
    @PreAuthorize(hasPermi = "dynamic:docTemplate:edit")
    @Log(title = "单据打印模板")
    @PostMapping("/template/update")
    public CommonResult<DynamicDocTemplate> update(@RequestBody DynamicDocTemplate template) {
        return CommonResult.ok(docTemplateService.updateTemplate(template));
    }

    @Operation(summary = "删除模板")
    @PreAuthorize(hasPermi = "dynamic:docTemplate:delete")
    @Log(title = "单据打印模板")
    @PostMapping("/template/{id}/delete")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        docTemplateService.deleteTemplate(id);
        return CommonResult.ok(Boolean.TRUE);
    }
}
