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

import com.privernal.common.log.enums.OperationType;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.dbs.util.DynamicTableAuthUtil;
import com.privernal.dynamic.report.domain.DynamicDashboard;
import com.privernal.dynamic.report.domain.DynamicDashboardItem;
import com.privernal.dynamic.report.domain.DynamicDataset;
import com.privernal.dynamic.report.domain.DynamicReport;
import com.privernal.dynamic.report.service.IDynamicDashboardService;
import com.privernal.dynamic.report.service.IDynamicDatasetService;
import com.privernal.dynamic.report.service.IDynamicReportService;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 问数存图 Controller（B8 Chat BI 方案B）。
 * <p>
 * 权限口径：菜单级 report:data:analyze（问数页），**不要求** report:dataset:add /
 * report:report:add 设计权限——业务用户把问数结果存成报表走本端点，内部代为落库：
 * 数据集按「{表名}·AI 分析」命名持久化，图表走原生 save 链（config 两期校验不绕过）。
 * 数据权限仍由 dynamic:{tableCode}:list + 行级数据范围兜底（同 /back/report/data/chat）。
 *
 * @author saperliu
 */
@Tag(name = "AI 问数存图相关接口")
@RestController
@Slf4j
@RequestMapping("/back/report/chat")
public class DynamicReportChatController extends BaseController {

    @Autowired
    private IDynamicDatasetService datasetService;

    @Autowired
    private IDynamicReportService reportService;

    @Autowired
    private IDynamicDashboardService dashboardService;

    @Autowired
    private com.privernal.dynamic.script.service.AiArtifactLedgerService artifactLedgerService;

    @Autowired
    private com.privernal.dynamic.report.service.DynamicReportTableResolver tableResolver;

    /**
     * 把问数结果存为报表图表。body: {tableCode|datasetCode, reportName, appId?, config}。
     * datasetCode 缺省时新建持久数据集（「{表名}·AI 分析」），同一张表重复保存会复用快照
     * 重新解析（列快照服务端强制重建，不信任提交值）。
     */
    @Operation(summary = "AI 问数存图", description = "body: {tableCode|datasetCode, reportName, appId?, config:{reportType,dimensions,measures,...}}")
    @PreAuthorize(hasPermi = "report:data:analyze")
    @Log(title = "报表图表", operationType = OperationType.INSERT)
    @PostMapping("/save-chart")
    public CommonResult<Object> saveChart(@RequestBody Map<String, Object> body) {
        String tableCode = StringUtils.trimToEmpty((String) body.get("tableCode"));
        String datasetCode = StringUtils.trimToEmpty((String) body.get("datasetCode"));
        String reportName = StringUtils.trimToEmpty((String) body.get("reportName"));
        if (StringUtils.isBlank(reportName)) {
            return CommonResult.fail("reportName 不能为空");
        }
        if (reportName.length() > 60) {
            return CommonResult.fail("reportName 过长（≤60 字符）");
        }
        JSONObject config = body.get("config") == null
                ? new JSONObject() : JSONObject.from(body.get("config"));

        DynamicDataset dataset;
        boolean datasetExisted;
        if (StringUtils.isNotBlank(datasetCode)) {
            dataset = datasetService.findByCode(datasetCode);
            if (dataset == null) {
                throw new ServiceException("数据集不存在：" + datasetCode);
            }
            datasetExisted = true;
        } else if (StringUtils.isNotBlank(tableCode)) {
            datasetExisted = datasetService.findByCode(analyzeDatasetCode(tableCode)) != null;
            // M3-2：config.joins（白名单关联）随保存链透传，服务端重解析后并入数据集快照
            dataset = buildPersistentAnalyzeDataset(tableCode, StringUtils.trimToEmpty((String) body.get("appId")),
                    config);
        } else {
            return CommonResult.fail("tableCode 与 datasetCode 至少传一个");
        }
        requireTablePermission(dataset);

        DynamicReport report = new DynamicReport();
        report.setReportCode("ai_chat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        report.setReportName(reportName);
        report.setDatasetCode(dataset.getDatasetCode());
        report.setAppId(StringUtils.isNotBlank((String) body.get("appId"))
                ? (String) body.get("appId") : dataset.getAppId());
        report.setReportType(config.getString("reportType"));
        report.setConfig(config);
        DynamicReport saved = reportService.saveDynamicReport(report);
        // B3 生成物台账：sessionId 留空由 ledger 从请求头读 X-AI-Session（编排层转发的请求自动归属会话）
        try {
            artifactLedgerService.record(null, "REPORT", saved.getReportCode(), saved.getReportCode());
        } catch (Exception e) {
            log.warn("生成物台账登记失败 report={}", saved.getReportCode(), e);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("reportCode", saved.getReportCode());
        out.put("reportName", saved.getReportName());
        out.put("datasetCode", saved.getDatasetCode());
        out.put("datasetCreated", !datasetExisted);
        return CommonResult.ok(out);
    }

    /**
     * 把已保存的问数图表投放到报表仪表盘（B8-M3-3a）。body: {reportCode, dashboardCode?, newDashboardName?}。
     * <p>
     * 权限口径：菜单级 report:data:analyze，**不要求** report:dashboard:add——目标仪表盘
     * 必须归属本组织、图表必须归属本人/本组织，否则拒绝；新建仪表盘复用 saveDashboard 原生链
     * （自动注册菜单 report_{code}），追加卡片走 items 全量重建（orderNum 顺延，span 默认 12）。
     */
    @Operation(summary = "AI 问数图表投放仪表盘", description = "body: {reportCode, dashboardCode?, newDashboardName?}；dashboardCode 缺省时按 newDashboardName 新建")
    @PreAuthorize(hasPermi = "report:data:analyze")
    @Log(title = "仪表盘", operationType = OperationType.INSERT)
    @PostMapping("/add-dashboard")
    public CommonResult<Object> addDashboard(@RequestBody Map<String, Object> body) {
        String reportCode = StringUtils.trimToEmpty((String) body.get("reportCode"));
        if (StringUtils.isBlank(reportCode)) {
            return CommonResult.fail("reportCode 不能为空");
        }
        DynamicReport report = reportService.findByCode(reportCode);
        if (report == null) {
            return CommonResult.fail("图表不存在：" + reportCode);
        }
        String userId = SecurityUtils.getUserId();
        String orgId = SecurityUtils.getOrganizationId();
        boolean reportOwned = (userId != null && userId.equals(report.getCreatedBy()))
                || (StringUtils.isNotBlank(report.getOrganizationId()) && report.getOrganizationId().equals(orgId));
        if (!reportOwned) {
            throw new ServiceException("只能投放自己保存的图表");
        }

        String dashboardCode = StringUtils.trimToEmpty((String) body.get("dashboardCode"));
        String newDashboardName = StringUtils.trimToEmpty((String) body.get("newDashboardName"));
        if (StringUtils.isNotBlank(newDashboardName) && newDashboardName.length() > 60) {
            return CommonResult.fail("newDashboardName 过长（≤60 字符）");
        }

        DynamicDashboard dashboard;
        boolean dashboardCreated;
        List<DynamicDashboardItem> items;
        if (StringUtils.isNotBlank(dashboardCode)) {
            dashboard = dashboardService.findByCode(dashboardCode);
            if (dashboard == null) {
                return CommonResult.fail("仪表盘不存在：" + dashboardCode);
            }
            if (!orgId.equals(dashboard.getOrganizationId())) {
                throw new ServiceException("只能投放到本组织的仪表盘");
            }
            dashboardCreated = false;
            items = new ArrayList<>(dashboardService.findDetail(dashboardCode).getItems());
        } else {
            if (StringUtils.isBlank(report.getAppId())) {
                return CommonResult.fail("图表未归属应用，无法新建仪表盘；请指定已有仪表盘（dashboardCode）");
            }
            dashboard = new DynamicDashboard();
            dashboard.setDashboardCode("ai_dash_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            dashboard.setDashboardName(StringUtils.isNotBlank(newDashboardName) ? newDashboardName : "AI 问数仪表盘");
            dashboard.setAppId(report.getAppId());
            dashboardCreated = true;
            items = new ArrayList<>();
        }

        int nextOrder = items.stream()
                .mapToInt(i -> i.getOrderNum() == null ? 0 : i.getOrderNum())
                .max().orElse(-1) + 1;
        DynamicDashboardItem item = new DynamicDashboardItem();
        item.setReportCode(report.getReportCode());
        item.setTitle(report.getReportName());
        item.setSpan(12);
        item.setOrderNum(nextOrder);
        items.add(item);
        dashboardService.saveDashboard(dashboard, items);

        Map<String, Object> out = new HashMap<>();
        out.put("dashboardCode", dashboard.getDashboardCode());
        out.put("dashboardName", dashboard.getDashboardName());
        out.put("dashboardCreated", dashboardCreated);
        out.put("reportCode", report.getReportCode());
        out.put("itemCount", items.size());
        return CommonResult.ok(out);
    }

    /** 问数数据集确定性编码：dataset_code 唯一索引 + varchar(100)，非安全字符归一为下划线并限长 */
    private static String analyzeDatasetCode(String tableCode) {
        String sanitized = tableCode.replaceAll("[^0-9a-zA-Z_]", "_");
        if (sanitized.length() > 88) {
            sanitized = sanitized.substring(0, 88);
        }
        return "ds_ai_" + sanitized;
    }

    /** 问数数据集持久化：确定性编码（同一张表只落一个 AI 分析数据集），经保存链 upsert（快照服务端强制重解析） */
    private DynamicDataset buildPersistentAnalyzeDataset(String tableCode, String appId, JSONObject config) {
        // M3-2：joins 意图随保存链透传——保存链会按登记关系重解析并合并关联列进快照
        DynamicDataset transientDs = datasetService.buildTransientDataset(tableCode,
                config == null ? null : config.getJSONArray("joins"));
        DynamicDataset dataset = new DynamicDataset();
        dataset.setDatasetCode(analyzeDatasetCode(tableCode));
        dataset.setDatasetName(transientDs.getDatasetName() + "·AI 分析");
        dataset.setTableCode(tableCode);
        dataset.setAppId(StringUtils.isNotBlank(appId) ? appId : transientDs.getAppId());
        dataset.setSourceType("dynamic");
        dataset.setColumnSnapshot(transientDs.getColumnSnapshot());
        return datasetService.saveDynamicDataset(dataset);
    }

    /**
     * 与 DynamicReportDataController.requireTablePermission 同口径（B8 两端点共用该语义）：
     * 查看者必须对数据集底层动态表有 list 按钮权限；表名解析不到时跳过按钮校验，
     * 交给聚合查询报「表不存在」——不因解析失败误放行也不误拒绝。
     */
    private void requireTablePermission(DynamicDataset dataset) {
        String physicalTable = tableResolver.resolvePhysicalTableCode(dataset.getTableCode());
        if (physicalTable == null) {
            return;
        }
        DynamicTableAuthUtil.requirePermission(physicalTable, "list");
    }
}
