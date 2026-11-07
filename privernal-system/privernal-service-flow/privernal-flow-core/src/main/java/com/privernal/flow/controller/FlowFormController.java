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

import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.flow.domain.FlowFormBind;
import com.privernal.flow.domain.FlowFormNodeConfig;
import com.privernal.flow.dto.FlowFormPermissionDTO;
import com.privernal.flow.service.IFlowFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程表单Controller（动态表单绑定、节点字段/按钮权限）
 *
 * @author saperliu
 */
@Tag(name = "流程表单相关接口")
@RestController
@RequestMapping("/back/flow/form")
public class FlowFormController extends BaseController {

    @Autowired
    private IFlowFormService flowFormService;

    /**
     * 查询流程表单绑定列表
     */
    @Operation(summary = "查询流程表单绑定列表")
    @PreAuthorize(hasPermi = "flow:form:list")
    @PostMapping("/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody FlowFormBind query) {
        startPage(query.getPageNum(), query.getPageSize(), query.getOrderBy(), query.getDirection());
        List<FlowFormBind> list = flowFormService.selectBindList(query);
        return getDataTable(list);
    }

    /**
     * 绑定详情
     */
    @Operation(summary = "绑定详情")
    @PreAuthorize(hasPermi = "flow:form:query")
    @GetMapping("/{id}")
    public CommonResult<FlowFormBind> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(flowFormService.findBindByTableCode(id));
    }

    /**
     * 新增绑定
     */
    @Operation(summary = "新增流程表单绑定")
    @PreAuthorize(hasPermi = "flow:form:add")
    @Log(title = "流程表单绑定", operationType = OperationType.INSERT)
    @PostMapping("/save")
    public CommonResult<Object> save(@RequestBody FlowFormBind bind) {
        return toAjax(flowFormService.saveBind(bind));
    }

    /**
     * 修改绑定
     */
    @Operation(summary = "修改流程表单绑定")
    @PreAuthorize(hasPermi = "flow:form:edit")
    @Log(title = "流程表单绑定", operationType = OperationType.UPDATE)
    @PutMapping("/edit")
    public CommonResult<Object> edit(@RequestBody FlowFormBind bind) {
        return toAjax(flowFormService.updateBind(bind));
    }

    /**
     * 删除绑定
     */
    @Operation(summary = "删除流程表单绑定")
    @PreAuthorize(hasPermi = "flow:form:remove")
    @Log(title = "流程表单绑定", operationType = OperationType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(flowFormService.deleteBindByIds(ids));
    }

    /**
     * 查询流程的节点表单配置列表
     */
    @Operation(summary = "查询节点表单配置列表")
    @PreAuthorize(hasPermi = "flow:form:query")
    @GetMapping("/node-config/list")
    public CommonResult<List<FlowFormNodeConfig>> nodeConfigList(@RequestParam("definitionCode") String definitionCode) {
        return CommonResult.ok(flowFormService.selectNodeConfigList(definitionCode));
    }

    /**
     * 保存节点表单配置（字段/按钮权限）
     */
    @Operation(summary = "保存节点表单配置")
    @PreAuthorize(hasPermi = "flow:form:edit")
    @Log(title = "节点表单配置", operationType = OperationType.UPDATE)
    @PostMapping("/node-config/save")
    public CommonResult<Object> saveNodeConfig(@RequestBody FlowFormNodeConfig nodeConfig) {
        return toAjax(flowFormService.saveNodeConfig(nodeConfig));
    }

    /**
     * 运行时表单权限（当前节点字段/按钮权限 + 当前用户是否审批人）
     */
    @Operation(summary = "运行时表单权限")
    @PostMapping("/runtime/field-permission")
    public CommonResult<FlowFormPermissionDTO> runtimePermission(@RequestParam("tableCode") String tableCode,
                                                                 @RequestParam("businessId") String businessId) {
        return CommonResult.ok(flowFormService.getRuntimePermission(tableCode, businessId));
    }
}
