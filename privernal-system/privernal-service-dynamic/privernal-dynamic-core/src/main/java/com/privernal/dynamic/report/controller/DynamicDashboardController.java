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
import com.privernal.dynamic.report.domain.DynamicDashboard;
import com.privernal.dynamic.report.domain.DynamicDashboardItem;
import com.privernal.dynamic.report.service.IDynamicDashboardService;
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
import java.util.Map;

/**
 * 报表仪表盘 Controller
 *
 * @author saperliu
 */
@Tag(name = "报表仪表盘相关接口")
@RestController
@Slf4j
@RequestMapping("/back/report/dashboard")
public class DynamicDashboardController extends BaseController {

    @Autowired
    private IDynamicDashboardService dashboardService;

    /**
     * 保存仪表盘（含卡片全量重建；新增时自动注册菜单）
     */
    @Operation(summary = "保存仪表盘", description = "body: {dashboard: {...}, items: [{reportCode,span,orderNum,title}]}")
    @PreAuthorize(hasPermi = "report:dashboard:add")
    @Log(title = "报表仪表盘", operationType = OperationType.UPDATE)
    @PostMapping("/save")
    public CommonResult<Object> save(@RequestBody Map<String, Object> body) {
        DynamicDashboard dashboard = com.alibaba.fastjson2.JSONObject.from(body.get("dashboard")).to(DynamicDashboard.class);
        List<DynamicDashboardItem> items = new java.util.ArrayList<>();
        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object item : list) {
                items.add(com.alibaba.fastjson2.JSONObject.from(item).to(DynamicDashboardItem.class));
            }
        }
        return CommonResult.ok(dashboardService.saveDashboard(dashboard, items));
    }

    /**
     * 仪表盘详情（含卡片与图表配置）
     */
    @Operation(summary = "仪表盘详情")
    @PreAuthorize(hasPermi = "report:dashboard:query")
    @GetMapping("/detail/{dashboardCode}")
    public CommonResult<Object> detail(@PathVariable("dashboardCode") String dashboardCode) {
        return CommonResult.ok(dashboardService.findDetail(dashboardCode));
    }

    /**
     * 仪表盘列表（按应用）
     */
    @Operation(summary = "仪表盘列表")
    @PreAuthorize(hasPermi = "report:dashboard:list")
    @GetMapping("/list")
    public CommonResult<Object> list(@RequestParam("appId") String appId) {
        return CommonResult.ok(dashboardService.listByApp(appId));
    }

    /**
     * 删除仪表盘
     */
    @Operation(summary = "删除仪表盘", description = "级联删卡片并清理自动注册的菜单与授权")
    @PreAuthorize(hasPermi = "report:dashboard:remove")
    @Log(title = "报表仪表盘", operationType = OperationType.DELETE)
    @DeleteMapping("/{dashboardCode}")
    public CommonResult<Object> remove(@PathVariable("dashboardCode") String dashboardCode) {
        if (StringUtils.isBlank(dashboardCode)) {
            return CommonResult.fail("仪表盘编码不能为空");
        }
        return toAjax(dashboardService.deleteByCode(dashboardCode));
    }
}
