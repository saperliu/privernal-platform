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

package com.privernal.openapi.controller;

import com.privernal.common.log.enums.OperationType;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.dba.vo.PageResultInfo;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.privernal.flow.dto.FlowInstanceStatusDTO;
import com.privernal.openapi.service.OpenApiDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外机器接口。鉴权不在这里：/openapi/** 由 OpenApiAuthFilter 验签并按凭证的表+动作白名单放行，
 * 所以这里没有 @PreAuthorize，也不能依赖 SecurityUtils 取身份。
 */
@Tag(name = "OpenAPI开放接口")
@RestController
@Slf4j
@RequestMapping("/openapi")
public class OpenApiController {

    @Autowired
    private OpenApiDataService openApiDataService;

    @Operation(summary = "分页查询动态表数据")
    @PostMapping("/data/{tableCode}/list")
    public CommonResult<Object> list(@PathVariable("tableCode") String tableCode,
                                     @RequestBody(required = false) Map<String, Object> query) {
        PageResultInfo pageResult = openApiDataService.listData(tableCode,
                query == null ? new HashMap<>() : query);
        return CommonResult.ok(pageResult);
    }

    @Operation(summary = "按主键读取一条动态表数据")
    @GetMapping("/data/{tableCode}/{id}")
    public CommonResult<Object> read(@PathVariable("tableCode") String tableCode,
                                     @PathVariable("id") String id) {
        return CommonResult.ok(openApiDataService.readData(tableCode, id), "查询成功");
    }

    @Operation(summary = "新增动态表数据")
    @Log(title = "开放接口数据", operationType = OperationType.INSERT)
    @PostMapping("/data/{tableCode}/add")
    public CommonResult<Object> add(@PathVariable("tableCode") String tableCode,
                                    @RequestBody Map<String, Object> data) {
        return CommonResult.ok(openApiDataService.addData(tableCode, data), "新增成功");
    }

    @Operation(summary = "修改动态表数据（请求体须含 id）")
    @Log(title = "开放接口数据", operationType = OperationType.UPDATE)
    @PutMapping("/data/{tableCode}/edit")
    public CommonResult<Object> edit(@PathVariable("tableCode") String tableCode,
                                     @RequestBody Map<String, Object> data) {
        return CommonResult.ok(openApiDataService.editData(tableCode, data), "修改成功");
    }

    @Operation(summary = "删除动态表数据")
    @Log(title = "开放接口数据", operationType = OperationType.DELETE)
    @DeleteMapping("/data/{tableCode}/{id}")
    public CommonResult<Object> remove(@PathVariable("tableCode") String tableCode,
                                       @PathVariable("id") String id) {
        openApiDataService.removeData(tableCode, id);
        return CommonResult.ok(id, "删除成功");
    }

    @Operation(summary = "查询动态表列定义")
    @GetMapping("/meta/{tableCode}/columns")
    public CommonResult<Object> columns(@PathVariable("tableCode") String tableCode) {
        List<DynamicFormColumnDTO> columns = openApiDataService.columns(tableCode);
        return CommonResult.ok(columns);
    }

    @Operation(summary = "发起流程（请求体 {tableCode, businessId, variables}）")
    @Log(title = "开放接口流程", operationType = OperationType.INSERT)
    @PostMapping("/flow/start")
    public CommonResult<Object> startFlow(@RequestBody Map<String, Object> body) {
        FlowInstanceStatusDTO status = openApiDataService.startFlow(body);
        return CommonResult.ok(status, "发起成功");
    }
}
