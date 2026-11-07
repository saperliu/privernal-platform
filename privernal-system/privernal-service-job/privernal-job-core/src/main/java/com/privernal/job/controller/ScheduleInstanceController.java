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

import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.job.service.ScheduleTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 实例状态与配额接口。
 *
 * @author saperliu
 */
@Slf4j
@Tag(name = "调度实例与配额接口")
@RestController
@RequestMapping("/back/schedule")
@RequiredArgsConstructor
public class ScheduleInstanceController extends BaseController {

    private final ScheduleTaskService scheduleTaskService;

    @Operation(summary = "实例心跳/角色/锁状态")
    @PreAuthorize(hasPermi = "schedule:instance:list")
    @GetMapping("/instance/list")
    public CommonResult<Map<String, Object>> instances() {
        return CommonResult.ok(scheduleTaskService.instanceState());
    }

    @Operation(summary = "配额水位")
    @PreAuthorize(hasPermi = "schedule:task:list")
    @GetMapping("/quota")
    public CommonResult<Map<String, Object>> quota() {
        return CommonResult.ok(scheduleTaskService.quota());
    }

    /** 任务报警列表（D5-C 一期落点） */
    @Operation(summary = "任务报警列表（全部告警项查库聚合+阈值判定）")
    @PreAuthorize(hasPermi = "schedule:alarm:list")
    @GetMapping("/alarm/list")
    public CommonResult<Map<String, Object>> alarms() {
        return CommonResult.ok(scheduleTaskService.alarmList());
    }
}
