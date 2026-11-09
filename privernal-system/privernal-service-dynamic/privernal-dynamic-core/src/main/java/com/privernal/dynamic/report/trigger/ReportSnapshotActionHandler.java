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

package com.privernal.dynamic.report.trigger;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.privernal.dynamic.advanced.engine.InnerMessageSender;
import com.privernal.dynamic.report.domain.DynamicDataset;
import com.privernal.dynamic.report.domain.DynamicReport;
import com.privernal.dynamic.report.service.DynamicReportQueryService;
import com.privernal.dynamic.report.service.IDynamicDatasetService;
import com.privernal.dynamic.report.service.IDynamicReportService;
import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报表定时快照（阶段3 6.3）：到点执行报表查询，把结果摘要（总行数+前 N 行要点）
 * 以站内信推送给订阅人——在线用户即时弹通知，离线用户留站内信可查。
 *
 * 任务 config：{reportCode, userIds:[], title?, topN?}；
 * databaseName 由调度内核按任务归属装配（ScheduleTaskSnapshot.databaseName）。
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportSnapshotActionHandler implements ScheduleActionHandler {

    private static final int DEFAULT_TOP_N = 5;

    private final IDynamicReportService reportService;
    private final IDynamicDatasetService datasetService;
    private final DynamicReportQueryService reportQueryService;
    private final ObjectProvider<InnerMessageSender> messageSenderProvider;

    @Override
    public String actionType() {
        return "REPORT_SNAPSHOT";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        Map<String, Object> config = context.getConfig();
        Object reportCodeObj = config.get("reportCode");
        if (reportCodeObj == null || String.valueOf(reportCodeObj).isBlank()) {
            return ActionResult.fail("config.reportCode 为空");
        }
        Object userIdsObj = config.get("userIds");
        if (!(userIdsObj instanceof List<?> rawIds) || rawIds.isEmpty()) {
            return ActionResult.fail("config.userIds 为空，无人可订阅");
        }
        int topN = config.get("topN") == null ? DEFAULT_TOP_N : Integer.parseInt(String.valueOf(config.get("topN")));

        DynamicReport report = reportService.findByCode(String.valueOf(reportCodeObj));
        if (report == null) {
            return ActionResult.fail("报表不存在：" + reportCodeObj);
        }
        DynamicDataset dataset = datasetService.findByCode(report.getDatasetCode());
        if (dataset == null) {
            return ActionResult.fail("报表数据集不存在：" + report.getDatasetCode());
        }
        Map<String, Object> result = reportQueryService.query(
                task.getDatabaseName(), report, dataset, new JSONArray());
        Object rowsObj = result.get("rows");
        int total = rowsObj instanceof List ? ((List<?>) rowsObj).size() : 0;

        StringBuilder content = new StringBuilder();
        content.append("定时快照：共 ").append(total).append(" 行（").append(task.getTitle()).append("）");
        if (rowsObj instanceof List<?> rows) {
            int shown = 0;
            for (Object row : rows) {
                if (shown >= topN) {
                    break;
                }
                if (row instanceof Map<?, ?> rowMap && !rowMap.isEmpty()) {
                    content.append('\n');
                    rowMap.forEach((key, value) -> content.append(key).append('=').append(value).append(' '));
                    shown++;
                }
            }
            if (total > shown) {
                content.append('\n').append("…… 其余 ").append(total - shown).append(" 行请到报表页查看");
            }
        }

        List<String> userIds = new ArrayList<>();
        for (Object id : rawIds) {
            userIds.add(String.valueOf(id));
        }
        InnerMessageSender sender = messageSenderProvider.getIfAvailable();
        if (sender == null) {
            return ActionResult.fail("站内信桥接不可用");
        }
        int delivered = sender.send(userIds,
                config.get("title") == null ? "报表快照：" + report.getReportName() : String.valueOf(config.get("title")),
                content.toString());
        return ActionResult.ok("snapshot rows=" + total + ", notified " + delivered + " users");
    }
}
