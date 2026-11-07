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

import com.privernal.dynamic.report.domain.DynamicDashboard;
import com.privernal.dynamic.report.domain.DynamicDashboardItem;

import java.util.List;

/**
 * 报表仪表盘服务
 *
 * @author saperliu
 */
public interface IDynamicDashboardService {

    /**
     * 保存仪表盘（新增/按编码更新），items 全量重建；新增时自动注册
     * 菜单 report_{code}（C）与 report:{code}:list/add/edit/remove（F 按钮）
     */
    DynamicDashboard saveDashboard(DynamicDashboard dashboard, List<DynamicDashboardItem> items);

    /**
     * 按编码查询仪表盘
     */
    DynamicDashboard findByCode(String dashboardCode);

    /**
     * 按编码查详情：仪表盘 + 卡片（含每卡片引用的图表）
     */
    DashboardDetail findDetail(String dashboardCode);

    /**
     * 按应用查询列表
     */
    List<DynamicDashboard> listByApp(String appId);

    /**
     * 删除仪表盘：级联删卡片并清理自动注册的菜单与授权
     */
    int deleteByCode(String dashboardCode);

    /**
     * 仪表盘详情
     */
    class DashboardDetail {
        private DynamicDashboard dashboard;
        private List<DynamicDashboardItem> items;
        /** 卡片引用的图表（reportCode → 图表），供运行时一次取全 */
        private List<com.privernal.dynamic.report.domain.DynamicReport> reports;

        public DynamicDashboard getDashboard() {
            return dashboard;
        }

        public void setDashboard(DynamicDashboard dashboard) {
            this.dashboard = dashboard;
        }

        public List<DynamicDashboardItem> getItems() {
            return items;
        }

        public void setItems(List<DynamicDashboardItem> items) {
            this.items = items;
        }

        public List<com.privernal.dynamic.report.domain.DynamicReport> getReports() {
            return reports;
        }

        public void setReports(List<com.privernal.dynamic.report.domain.DynamicReport> reports) {
            this.reports = reports;
        }
    }
}
