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

package com.privernal.job.action.impl;

import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleAction;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import com.privernal.job.config.ScheduleMetrics;
import com.privernal.job.config.ScheduleMetrics.MetricSample;
import com.privernal.job.mapper.ScheduleLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统动作：调度指标快照采集（D5-C 二期方案 B）。
 *
 * <p>SYSTEM 任务 metrics-snapshot（每 1 分钟）把应用内 MeterRegistry 的 schedule.*
 * 指标状态写进 system_schedule_metric_snapshot——报警列表消费 30 分钟窗口趋势
 * （扫描轮耗时 max/均值、执行增量、池拒绝增量等），零新增外部组件；
 * 与 /actuator/metrics、/actuator/prometheus（运维采集）互为并行出口。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduleAction("metrics-snapshot")
public class MetricsSnapshotActionHandler implements ScheduleActionHandler {

    private final ScheduleMetrics metrics;

    private final ScheduleLogMapper logMapper;

    @Override
    public String actionType() {
        return "BEAN";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        if (!metrics.enabled()) {
            return ActionResult.ok("SKIPPED: Micrometer 未启用");
        }
        List<MetricSample> samples = metrics.collectSnapshot();
        if (samples.isEmpty()) {
            return ActionResult.ok("no samples");
        }
        int rows = logMapper.insertMetricSnapshots(samples);
        return ActionResult.ok("snapshotted " + rows + " metric samples");
    }
}
