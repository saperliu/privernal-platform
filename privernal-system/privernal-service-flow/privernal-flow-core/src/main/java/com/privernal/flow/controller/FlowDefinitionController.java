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
import com.privernal.flow.dto.FlowDefinitionSynDTO;
import com.privernal.flow.filter.FlowDefinitionFilterDTO;
import com.privernal.flow.service.IFlowDefinitionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程定义Controller（配置、发布、停用等）
 *
 * @author saperliu
 */
@Tag(name = "流程定义相关接口")
@RestController
@RequestMapping("/back/flow/definition")
public class FlowDefinitionController extends BaseController {

    @Autowired
    private IFlowDefinitionService flowDefinitionService;

    @Autowired
    private com.privernal.flow.service.impl.FlowDefinitionValidateService flowDefinitionValidateService;

    /**
     * 查询流程定义列表
     */
    @Operation(summary = "查询流程定义列表")
    @PreAuthorize(hasPermi = "flow:definition:list")
    @PostMapping("/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody FlowDefinitionFilterDTO filter) {
        Page<Definition> page = flowDefinitionService.pageDefinitions(filter);
        return CommonResult.ok(new PageListDTO<Object>(page.getRecords(), (int) page.getTotal()));
    }

    /**
     * 获取流程定义详情
     */
    @Operation(summary = "获取流程定义详情")
    @PreAuthorize(hasPermi = "flow:definition:query")
    @GetMapping("/{id}")
    public CommonResult<Definition> getInfo(@PathVariable("id") Long id) {
        return CommonResult.ok(flowDefinitionService.getDefinition(id));
    }

    /**
     * B1 同范式的流程定义纯校验（零副作用）：办理人角色存在性、列长触顶、结构红线的前置闸门，
     * 供 AI 一档工具面（validate_flow_definition）在 save 之前调用。
     * <p>
     * 刻意<b>不</b>挂进 {@code /save}：存量人工配置里已有失效角色，保存链强拦会挡住日常改流程。
     */
    @Operation(summary = "流程定义纯校验（零副作用，返回结构化错误清单）")
    @PreAuthorize(hasPermi = "flow:definition:add")
    @PostMapping("/validate")
    public CommonResult<Object> validate(@RequestBody DefJson defJson) {
        return CommonResult.ok(flowDefinitionValidateService.validateDefinition(defJson));
    }

    /**
     * 保存流程定义（设计器 JSON）
     */
    @Operation(summary = "保存流程定义")
    @PreAuthorize(hasPermi = "flow:definition:add")
    @Log(title = "流程定义", operationType = OperationType.INSERT)
    @PostMapping("/save")
    public CommonResult<Definition> save(@RequestBody DefJson defJson) {
        return CommonResult.ok(flowDefinitionService.saveDefJson(defJson));
    }

    /**
     * 发布流程定义
     */
    @Operation(summary = "发布流程定义")
    @PreAuthorize(hasPermi = "flow:definition:publish")
    @Log(title = "流程定义", operationType = OperationType.UPDATE)
    @PutMapping("/publish/{id}")
    public CommonResult<Object> publish(@PathVariable("id") Long id) {
        return toAjax(flowDefinitionService.publish(id));
    }

    /**
     * 停用流程定义（下架，不可再发起新流程）
     */
    @Operation(summary = "停用流程定义")
    @PreAuthorize(hasPermi = "flow:definition:publish")
    @Log(title = "流程定义", operationType = OperationType.UPDATE)
    @PutMapping("/unpublish/{id}")
    public CommonResult<Object> unPublish(@PathVariable("id") Long id) {
        return toAjax(flowDefinitionService.unPublish(id));
    }

    /**
     * 激活流程定义
     */
    @Operation(summary = "激活流程定义")
    @PreAuthorize(hasPermi = "flow:definition:publish")
    @Log(title = "流程定义", operationType = OperationType.UPDATE)
    @PutMapping("/active/{id}")
    public CommonResult<Object> active(@PathVariable("id") Long id) {
        return toAjax(flowDefinitionService.active(id));
    }

    /**
     * 挂起流程定义
     */
    @Operation(summary = "挂起流程定义")
    @PreAuthorize(hasPermi = "flow:definition:publish")
    @Log(title = "流程定义", operationType = OperationType.UPDATE)
    @PutMapping("/unactive/{id}")
    public CommonResult<Object> unActive(@PathVariable("id") Long id) {
        return toAjax(flowDefinitionService.unActive(id));
    }

    /**
     * 复制流程定义
     */
    @Operation(summary = "复制流程定义")
    @PreAuthorize(hasPermi = "flow:definition:add")
    @Log(title = "流程定义", operationType = OperationType.INSERT)
    @PostMapping("/copy/{id}")
    public CommonResult<Object> copy(@PathVariable("id") Long id) {
        return toAjax(flowDefinitionService.copyDef(id));
    }

    /**
     * 删除流程定义
     */
    @Operation(summary = "删除流程定义")
    @PreAuthorize(hasPermi = "flow:definition:remove")
    @Log(title = "流程定义", operationType = OperationType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(flowDefinitionService.removeDef(ids));
    }

    /**
     * 导出流程定义 JSON
     */
    @Operation(summary = "导出流程定义JSON")
    @PreAuthorize(hasPermi = "flow:definition:import")
    @GetMapping("/export/{id}")
    public CommonResult<String> exportJson(@PathVariable("id") Long id) {
        return CommonResult.ok(flowDefinitionService.exportJson(id));
    }

    /**
     * 按编码查询流程定义
     */
    @Operation(summary = "按编码查询流程定义")
    @PreAuthorize(hasPermi = "flow:definition:query")
    @GetMapping("/code/{flowCode}")
    public CommonResult<List<Definition>> getByCode(@PathVariable("flowCode") String flowCode) {
        return CommonResult.ok(FlowEngine.defService().getByFlowCode(flowCode));
    }

    /**
     * 同步初始化组织的流程配置（复制平台模板的表单绑定与节点权限配置）
     */
    @Operation(summary = "同步流程配置到组织")
    @PreAuthorize(hasPermi = "flow:definition:edit")
    @Log(title = "同步流程配置到组织", operationType = OperationType.UPDATE)
    @PutMapping("/syn")
    public CommonResult<Object> synDefinition(@RequestBody FlowDefinitionSynDTO synDTO) {
        int count = flowDefinitionService.initForOrganization(synDTO.getOrganizationId(), synDTO.getDepartmentId());
        return CommonResult.ok(count);
    }
}
