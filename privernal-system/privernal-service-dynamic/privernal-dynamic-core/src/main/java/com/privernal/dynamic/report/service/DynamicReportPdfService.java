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

package com.privernal.dynamic.report.service;

import com.privernal.dynamic.report.domain.DynamicDataset;
import com.privernal.dynamic.report.domain.DynamicReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报表服务端 PDF 导出（R2-12 ④）。
 * <p>
 * 取数与运行态 /query 走同一条 DynamicReportQueryService.query 链——行级数据范围、
 * 列快照校验、runtimeFilters 全部同一套收口，PDF 里的数据不可能比查看者在页面上
 * 看到的多（导出通道复用查询通道，不另开口径）。渲染是纯 HTML 表格，字体与
 * XHTML 归一化交给已投产的 DocPrintPdfService（openhtmltopdf + 中文字体三级解析）。
 *
 * @author saperliu
 */
@Service
public class DynamicReportPdfService {

    @Autowired
    private DynamicReportQueryService reportQueryService;

    @Autowired
    private com.privernal.dynamic.advanced.service.DocPrintPdfService docPrintPdfService;

    /**
     * 聚合结果渲染成表格 PDF 字节。
     *
     * @param runtimeFilters 运行时过滤 [{field,op,value}]，可为空（与页面当前全局筛选一致）
     */
    public byte[] renderReportPdf(String database, DynamicReport report, DynamicDataset dataset,
                                  com.alibaba.fastjson2.JSONArray runtimeFilters) {
        Map<String, Object> result = reportQueryService.query(database, report, dataset, runtimeFilters);
        return docPrintPdfService.renderPdf(buildTableHtml(report, result));
    }

    /**
     * 结果集 → 表格 HTML。列集按后端回传的 aliases/labels 全量输出（维度 + 度量，
     * 含同比 Compare 系列），行值优先取 lookup 已翻译的显示值（dim{i}），编码不进表。
     */
    private String buildTableHtml(DynamicReport report, Map<String, Object> result) {
        List<Map<String, Object>> rows = result.get("rows") instanceof List
                ? (List<Map<String, Object>>) result.get("rows") : new ArrayList<>();
        List<String> dimensionAliases = stringList(result, "dimensionAliases");
        List<String> measureAliases = stringList(result, "measureAliases");
        List<String> dimensionLabels = labelsOrAliases(result, "dimensionLabels", dimensionAliases);
        List<String> measureLabels = labelsOrAliases(result, "measureLabels", measureAliases);

        StringBuilder head = new StringBuilder();
        for (int i = 0; i < dimensionLabels.size(); i++) {
            head.append("<th>").append(escape(dimensionLabels.get(i))).append("</th>");
        }
        for (int i = 0; i < measureLabels.size(); i++) {
            head.append("<th class=\"num\">").append(escape(measureLabels.get(i))).append("</th>");
        }

        StringBuilder body = new StringBuilder();
        if (rows.isEmpty()) {
            body.append("<tr><td class=\"empty\" colspan=\"")
                    .append(Math.max(1, dimensionLabels.size() + measureLabels.size()))
                    .append("\">当前筛选条件下没有数据</td></tr>");
        }
        for (Map<String, Object> row : rows) {
            body.append("<tr>");
            for (String alias : dimensionAliases) {
                body.append("<td>").append(escape(displayValue(row.get(alias)))).append("</td>");
            }
            for (String alias : measureAliases) {
                body.append("<td class=\"num\">").append(escape(displayValue(row.get(alias)))).append("</td>");
            }
            body.append("</tr>");
        }

        String generatedAt = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String title = report.getReportName() == null || report.getReportName().isBlank()
                ? report.getReportCode() : report.getReportName();
        return "<html><head><meta charset=\"UTF-8\"/><style>"
                + "body { font-size: 12px; margin: 24px; }"
                + "h1 { font-size: 16px; margin: 0 0 4px 0; }"
                + ".sub { color: #666; font-size: 10px; margin-bottom: 14px; }"
                + "table { width: 100%; border-collapse: collapse; }"
                + "th, td { border: 1px solid #999; padding: 4px 8px; word-break: break-all; }"
                + "th { background: #f2f2f2; text-align: left; }"
                + "td.num, th.num { text-align: right; }"
                + "td.empty { text-align: center; color: #666; padding: 24px; }"
                + "</style></head><body>"
                + "<h1>" + escape(title) + "</h1>"
                + "<div class=\"sub\">生成时间：" + generatedAt + "（服务端导出）</div>"
                + "<table><thead><tr>" + head + "</tr></thead><tbody>" + body + "</tbody></table>"
                + "</body></html>";
    }

    private List<String> stringList(Map<String, Object> result, String key) {
        if (result.get(key) instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    /** labels 缺项时回退别名（与前端 aliasesOf 同口径），保证表头与列数恒一致 */
    private List<String> labelsOrAliases(Map<String, Object> result, String labelsKey, List<String> aliases) {
        List<String> labels = stringList(result, labelsKey);
        if (labels.size() >= aliases.size()) {
            return labels;
        }
        for (int i = labels.size(); i < aliases.size(); i++) {
            labels.add(aliases.get(i));
        }
        return labels;
    }

    /** null 显示为「—」（与前端 emptyText 口径一致），其余按原值字符串化 */
    private String displayValue(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    /** 字段名与值都可能来自用户配置，进 HTML 前一律转义 */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
