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

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.dbs.util.DynamicTableAuthUtil;
import com.privernal.dynamic.report.domain.DynamicDashboardItem;
import com.privernal.dynamic.report.domain.DynamicDataset;
import com.privernal.dynamic.report.domain.DynamicReport;
import com.privernal.dynamic.report.service.DynamicReportQueryService;
import com.privernal.dynamic.report.service.IDynamicDatasetService;
import com.privernal.dynamic.report.service.IDynamicDashboardService;
import com.privernal.dynamic.report.service.IDynamicReportService;
import com.privernal.dynamic.report.util.DynamicReportAuthUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 报表数据查询 Controller（方案A口径）
 * <p>
 * 双重鉴权：
 * 1. 菜单权限——设计态 preview 要求报表数据集查询权限；运行态 query 要求
 *    report:{dashboardCode}:view（来自仪表盘自动注册的菜单，编码在请求里，注解表达不了）
 * 2. 数据权限——查看者必须对数据集底层表有 dynamic:{tableCode}:list（DynamicTableAuthUtil），
 *    且聚合结果按查看者行级范围过滤（DynamicReportQueryMapper 的 DataScopeMapper 通道自动追加）
 *
 * @author saperliu
 */
@Tag(name = "报表数据查询相关接口")
@RestController
@Slf4j
@RequestMapping("/back/report/data")
public class DynamicReportDataController extends BaseController {

    @Autowired
    private DynamicReportQueryService reportQueryService;

    @Autowired
    private IDynamicDatasetService datasetService;

    @Autowired
    private IDynamicReportService reportService;

    @Autowired
    private IDynamicDashboardService dashboardService;

    @Autowired
    private com.privernal.dynamic.report.service.DynamicReportTableResolver tableResolver;

    /**
     * 设计态预览：数据集 + 未保存的图表配置
     */
    @Operation(summary = "图表预览", description = "body: {datasetCode, config:{reportType,dimensions,measures,filters,limit}}")
    @PreAuthorize(hasPermi = "report:dataset:query")
    @PostMapping("/preview")
    public CommonResult<Object> preview(@RequestBody Map<String, Object> body) {
        String datasetCode = StringUtils.trimToEmpty((String) body.get("datasetCode"));
        DynamicDataset dataset = datasetService.findByCode(datasetCode);
        if (dataset == null) {
            throw new ServiceException("数据集不存在：" + datasetCode);
        }
        requireTablePermission(dataset);
        // Spring 用 Jackson 绑定 body，嵌套对象是 LinkedHashMap，必须显式转 fastjson2 JSONObject
        JSONObject config = body.get("config") == null
                ? new JSONObject() : JSONObject.from(body.get("config"));
        String database = SecurityUtils.getDatabaseName();
        return CommonResult.ok(reportQueryService.preview(database, dataset, config));
    }

    /**
     * AI 问数取数（B8 Chat BI 方案B）：按 tableCode 构建临时数据集（不落库）或复用已有数据集，
     * 走与 preview 同一条聚合链。权限独立成 report:data:analyze——业务用户问数不需要
     * report:dataset:query 设计权限（open_data_view 的教训：数据通道不得复用设计态权限）；
     * 数据权限仍由 requireTablePermission（dynamic:{tableCode}:list）+ 行级数据范围兜底。
     */
    @Operation(summary = "AI 问数取数", description = "body: {tableCode|datasetCode, config:{reportType,dimensions,measures,filters,comparison,joins,limit}}")
    @PreAuthorize(hasPermi = "report:data:analyze")
    @PostMapping("/chat")
    public CommonResult<Object> chat(@RequestBody Map<String, Object> body) {
        String tableCode = StringUtils.trimToEmpty((String) body.get("tableCode"));
        String datasetCode = StringUtils.trimToEmpty((String) body.get("datasetCode"));
        JSONObject config = body.get("config") == null
                ? new JSONObject() : JSONObject.from(body.get("config"));
        DynamicDataset dataset;
        if (StringUtils.isNotBlank(datasetCode)) {
            dataset = datasetService.findByCode(datasetCode);
            if (dataset == null) {
                throw new ServiceException("数据集不存在：" + datasetCode);
            }
        } else if (StringUtils.isNotBlank(tableCode)) {
            // M3-2：config.joins 只表达「想关联什么」，服务端按登记关系重解析（未登记给可读 400）
            dataset = datasetService.buildTransientDataset(tableCode, config.getJSONArray("joins"));
        } else {
            return CommonResult.fail("tableCode 与 datasetCode 至少传一个");
        }
        requireTablePermission(dataset);
        String database = SecurityUtils.getDatabaseName();
        Map<String, Object> result = reportQueryService.preview(database, dataset, config);
        result.put("datasetCode", dataset.getDatasetCode());
        result.put("datasetName", dataset.getDatasetName());
        return CommonResult.ok(result);
    }

    /**
     * 运行态查询：仪表盘内的图表取数
     */
    @Operation(summary = "图表取数", description = "body: {dashboardCode, reportCode, filters?}")
    @PostMapping("/query")
    public CommonResult<Object> query(@RequestBody Map<String, Object> body) {
        String dashboardCode = StringUtils.trimToEmpty((String) body.get("dashboardCode"));
        String reportCode = StringUtils.trimToEmpty((String) body.get("reportCode"));
        if (StringUtils.isBlank(dashboardCode) || StringUtils.isBlank(reportCode)) {
            return CommonResult.fail("dashboardCode 与 reportCode 不能为空");
        }
        // 菜单权限：report:{dashboardCode}:view
        DynamicReportAuthUtil.requirePermission(dashboardCode, "view");
        // 图表必须真实挂在该仪表盘下：否则拿到 report:{A}:view 的人可以对任意 reportCode 取数
        IDynamicDashboardService.DashboardDetail detail = dashboardService.findDetail(dashboardCode);
        if (detail == null) {
            throw new ServiceException("仪表盘不存在：" + dashboardCode);
        }
        requireItemBelongsToDashboard(detail, reportCode, dashboardCode);
        DynamicReport report = reportService.findByCode(reportCode);
        if (report == null) {
            throw new ServiceException("图表不存在：" + reportCode);
        }
        DynamicDataset dataset = datasetService.findByCode(report.getDatasetCode());
        if (dataset == null) {
            throw new ServiceException("图表的数据集不存在：" + report.getDatasetCode());
        }
        requireTablePermission(dataset);

        JSONArray filters = body.get("filters") == null ? null : JSONArray.from(body.get("filters"));
        String database = SecurityUtils.getDatabaseName();
        return CommonResult.ok(reportQueryService.query(database, report, dataset, filters));
    }

    /**
     * 卡片归属校验：reportCode 必须出现在该仪表盘的 items 里。
     * 单有 report:{dashboardCode}:view 不够——否则任何人可以借自家看板的权限
     * 去取别家仪表盘的图表数据。
     */
    private void requireItemBelongsToDashboard(IDynamicDashboardService.DashboardDetail detail,
                                               String reportCode, String dashboardCode) {
        if (detail.getItems() != null) {
            for (DynamicDashboardItem item : detail.getItems()) {
                if (reportCode.equals(item.getReportCode())) {
                    return;
                }
            }
        }
        throw new ServiceException("图表 " + reportCode + " 未挂在仪表盘 " + dashboardCode + " 上");
    }

    /**
     * 方案A：查看者必须对数据集底层动态表有 list 按钮权限。
     * 表名经三级容错解析（与数据集保存/聚合查询同一解析），解析不到时跳过按钮校验，
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
