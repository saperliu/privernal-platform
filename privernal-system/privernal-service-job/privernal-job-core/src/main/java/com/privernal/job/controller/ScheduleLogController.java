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

package com.privernal.job.controller;

import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.job.domain.SystemScheduleTaskLog;
import com.privernal.job.filter.ScheduleLogFilterDTO;
import com.privernal.job.service.ScheduleTaskService;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 执行记录接口（；时间筛选一律 fire_time，分区键口径）。
 *
 * @author saperliu
 */
@Slf4j
@Tag(name = "定时任务执行记录接口")
@RestController
@RequestMapping("/back/schedule/log")
@RequiredArgsConstructor
public class ScheduleLogController extends BaseController {

    private final ScheduleTaskService scheduleTaskService;

    @Operation(summary = "执行记录分页（含 attempt=0 跳过行，与执行链同列展示）")
    @PreAuthorize(hasPermi = "schedule:log:list")
    @PostMapping("/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody ScheduleLogFilterDTO filter) {
        startPage(filter);
        List<SystemScheduleTaskLog> list = scheduleTaskService.listLogs(filter);
        return getDataTable(list);
    }

    @Operation(summary = "执行详情（含 action_snapshot）")
    @PreAuthorize(hasPermi = "schedule:log:list")
    @GetMapping("/{id}")
    public CommonResult<SystemScheduleTaskLog> info(@PathVariable("id") Long id) {
        return CommonResult.ok(scheduleTaskService.getLog(id));
    }

    @Operation(summary = "失败手动重跑")
    @PreAuthorize(hasPermi = "schedule:log:retry")
    @Log(title = "定时任务执行记录", operationType = OperationType.OTHER)
    @PostMapping("/{id}/retry")
    public CommonResult<Map<String, Object>> retry(@PathVariable("id") Long id) {
        return CommonResult.ok(scheduleTaskService.retryLog(id));
    }
}
