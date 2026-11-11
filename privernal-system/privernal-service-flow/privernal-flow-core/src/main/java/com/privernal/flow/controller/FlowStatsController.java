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

package com.privernal.flow.controller;

import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.flow.service.IFlowStatsService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 流程绩效看板 + 流程预测（R2-17）
 *
 * @author saperliu
 */
@RestController
@RequestMapping("/back/flow/stats")
public class FlowStatsController {

    @Autowired
    private IFlowStatsService statsService;

    /**
     * 实例总览（total/running/finished/terminated/avgDurationSeconds）
     */
    @Operation(summary = "实例总览")
    @PreAuthorize(hasPermi = "flow:task:list")
    @GetMapping("/overview")
    public CommonResult<Map<String, Object>> overview(@RequestParam(value = "timeStart", required = false) String timeStart,
                                                      @RequestParam(value = "timeEnd", required = false) String timeEnd) {
        return CommonResult.ok(statsService.overview(timeStart, timeEnd));
    }

    /**
     * 按流程定义统计
     */
    @Operation(summary = "按流程定义统计")
    @PreAuthorize(hasPermi = "flow:task:list")
    @GetMapping("/definition")
    public CommonResult<List<Map<String, Object>>> definitionStats(@RequestParam(value = "timeStart", required = false) String timeStart,
                                                                   @RequestParam(value = "timeEnd", required = false) String timeEnd,
                                                                   @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return CommonResult.ok(statsService.definitionStats(timeStart, timeEnd, limit));
    }

    /**
     * 按节点统计历史停留
     */
    @Operation(summary = "按节点统计历史停留")
    @PreAuthorize(hasPermi = "flow:task:list")
    @GetMapping("/node")
    public CommonResult<List<Map<String, Object>>> nodeStats(@RequestParam("definitionId") String definitionId) {
        return CommonResult.ok(statsService.nodeStats(definitionId));
    }

    /**
     * 在办负载 TOP N（按办理人标识聚合）
     */
    @Operation(summary = "在办负载")
    @PreAuthorize(hasPermi = "flow:task:list")
    @GetMapping("/handler")
    public CommonResult<List<Map<String, Object>>> handlerWorkload(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        return CommonResult.ok(statsService.handlerWorkload(limit));
    }

    /**
     * 流程预测：按历史平均停留预测实例剩余流转时间
     */
    @Operation(summary = "流程预测")
    @PreAuthorize(hasPermi = "flow:task:list")
    @GetMapping("/predict/{instanceId}")
    public CommonResult<Map<String, Object>> predict(@PathVariable("instanceId") Long instanceId) {
        return CommonResult.ok(statsService.predict(instanceId));
    }
}
