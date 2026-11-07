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
import com.privernal.job.domain.SystemScheduleTask;
import com.privernal.job.filter.ScheduleTaskFilterDTO;
import com.privernal.job.service.ScheduleTaskService;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 任务管理接口（/system/back/schedule/task/**，sa-token + @PreAuthorize）。
 *
 * <p>权限码与菜单 SQL、@PrivernalDataScope 拼码三处一致（§9.4；注意切面拼 :remove，
 * 但管理 Controller 沿用本仓 :delete 惯例，SQL 权限码按此处为准）。</p>
 *
 * @author saperliu
 */
@Slf4j
@Tag(name = "定时任务管理接口")
@RestController
@RequestMapping("/back/schedule/task")
@RequiredArgsConstructor
public class ScheduleTaskController extends BaseController {

    private final ScheduleTaskService scheduleTaskService;

    @Operation(summary = "任务分页列表")
    @PreAuthorize(hasPermi = "schedule:task:list")
    @PostMapping("/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody ScheduleTaskFilterDTO filter) {
        startPage(filter);
        List<SystemScheduleTask> list = scheduleTaskService.list(filter);
        return getDataTable(list);
    }

    @Operation(summary = "任务详情")
    @PreAuthorize(hasPermi = "schedule:task:query")
    @GetMapping("/{id}")
    public CommonResult<SystemScheduleTask> info(@PathVariable("id") String id) {
        return CommonResult.ok(scheduleTaskService.getById(id));
    }

    @Operation(summary = "新建任务(MANUAL)")
    @PreAuthorize(hasPermi = "schedule:task:add")
    @Log(title = "定时任务", operationType = OperationType.INSERT)
    @PostMapping
    public CommonResult<Map<String, Object>> add(@RequestBody SystemScheduleTask form) {
        return CommonResult.ok(scheduleTaskService.save(form));
    }

    @Operation(summary = "编辑任务(version+1)")
    @PreAuthorize(hasPermi = "schedule:task:edit")
    @Log(title = "定时任务", operationType = OperationType.UPDATE)
    @PutMapping("/{id}")
    public CommonResult<Map<String, Object>> edit(@PathVariable("id") String id, @RequestBody SystemScheduleTask form) {
        form.setId(id);
        return CommonResult.ok(scheduleTaskService.save(form));
    }

    @Operation(summary = "删除任务(软删+CANCEL)")
    @PreAuthorize(hasPermi = "schedule:task:delete")
    @Log(title = "定时任务", operationType = OperationType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Object> remove(@PathVariable String[] ids) {
        return toAjax(scheduleTaskService.delete(Arrays.asList(ids)));
    }

    @Operation(summary = "启用/暂停")
    @PreAuthorize(hasPermi = "schedule:task:edit")
    @Log(title = "定时任务", operationType = OperationType.UPDATE)
    @PutMapping("/{id}/status")
    public CommonResult<Map<String, Object>> changeStatus(@PathVariable("id") String id, @RequestParam("status") String status) {
        return CommonResult.ok(scheduleTaskService.changeStatus(id, status));
    }

    @Operation(summary = "按应用/来源批量启停")
    @PreAuthorize(hasPermi = "schedule:task:pause-batch")
    @Log(title = "定时任务", operationType = OperationType.UPDATE)
    @PutMapping("/status/batch")
    public CommonResult<Object> batchStatus(@RequestParam(value = "appId", required = false) String appId,
                                            @RequestParam(value = "source", required = false) String source,
                                            @RequestParam("status") String status) {
        return toAjax(scheduleTaskService.batchChangeStatus(appId, source, status));
    }

    @Operation(summary = "手动执行")
    @PreAuthorize(hasPermi = "schedule:task:run")
    @Log(title = "定时任务", operationType = OperationType.OTHER)
    @PostMapping("/{id}/run")
    public CommonResult<Map<String, Object>> run(@PathVariable("id") String id) {
        return CommonResult.ok(scheduleTaskService.run(id));
    }

    @Operation(summary = "终止运行中")
    @PreAuthorize(hasPermi = "schedule:task:terminate")
    @Log(title = "定时任务", operationType = OperationType.OTHER)
    @PostMapping("/{id}/terminate")
    public CommonResult<Map<String, Object>> terminate(@PathVariable("id") String id) {
        return CommonResult.ok(scheduleTaskService.terminate(id));
    }

    @Operation(summary = "未来 N 次触发预览")
    @PreAuthorize(hasPermi = "schedule:task:query")
    @GetMapping("/{id}/preview")
    public CommonResult<Map<String, Object>> preview(@PathVariable("id") String id, @RequestParam(value = "n", defaultValue = "5") int n) {
        return CommonResult.ok(scheduleTaskService.preview(id, n));
    }

    @Operation(summary = "cron 表达式校验与预览")
    @PreAuthorize(hasPermi = "schedule:task:query")
    @GetMapping("/cron-preview")
    public CommonResult<Object> cronPreview(@RequestParam("cron") String cron, @RequestParam(value = "n", defaultValue = "5") int n) {
        List<java.util.Date> next = scheduleTaskService.previewNext(cron, n);
        return CommonResult.ok(Map.of("valid", true, "fireTimes", next));
    }
}
