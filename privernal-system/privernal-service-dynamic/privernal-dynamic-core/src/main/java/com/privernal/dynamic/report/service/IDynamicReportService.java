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

import com.privernal.dynamic.report.domain.DynamicReport;

import java.util.List;

/**
 * 报表图表服务
 *
 * @author saperliu
 */
public interface IDynamicReportService {

    /**
     * 保存图表（新增/按编码更新）
     */
    DynamicReport saveDynamicReport(DynamicReport report);

    /**
     * 按编码查询
     */
    DynamicReport findByCode(String reportCode);

    /**
     * 按应用查询列表
     */
    List<DynamicReport> listByApp(String appId);

    /**
     * 删除（被仪表盘卡片引用时拒绝）
     */
    int deleteByCode(String reportCode);
}
