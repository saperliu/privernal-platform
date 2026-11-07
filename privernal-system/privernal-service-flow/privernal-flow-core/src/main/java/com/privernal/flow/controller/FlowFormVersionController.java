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
import com.privernal.flow.dto.FlowFormVersionDTO;
import com.privernal.flow.service.IFlowFormVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程表单配置版本Controller（版本控制、草稿/发布、节点字段/按钮权限明细）
 * 替代旧 wfs 的 workflow_instance_form* 7 张表的表单权限能力
 *
 * @author saperliu
 */
@Tag(name = "流程表单配置版本相关接口")
@RestController
@RequestMapping("/back/flow/form/version")
public class FlowFormVersionController extends BaseController {

    @Autowired
    private IFlowFormVersionService flowFormVersionService;

    /**
     * 查询版本列表
     */
    @Operation(summary = "查询表单配置版本列表")
    @PreAuthorize(hasPermi = "flow:form:list")
    @GetMapping("/list")
    public CommonResult<List<FlowFormVersionDTO>> list(@RequestParam(value = "flowCode", required = false) String flowCode,
                                                       @RequestParam(value = "tableCode", required = false) String tableCode,
                                                       @RequestParam(value = "publishStatus", required = false) Integer publishStatus) {
        return CommonResult.ok(flowFormVersionService.selectVersionList(flowCode, tableCode, publishStatus));
    }

    /**
     * 版本详情（含字段/按钮权限明细）
     */
    @Operation(summary = "查询表单配置版本详情")
    @PreAuthorize(hasPermi = "flow:form:query")
    @GetMapping("/{id}")
    public CommonResult<FlowFormVersionDTO> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(flowFormVersionService.selectVersionDetail(id));
    }

    /**
     * 初始化草稿（字段全集取自动态表单模块，按钮全集取内置按钮枚举）
     */
    @Operation(summary = "初始化表单配置草稿")
    @PreAuthorize(hasPermi = "flow:form:add")
    @Log(title = "流程表单配置版本", operationType = OperationType.INSERT)
    @PostMapping("/init")
    public CommonResult<Object> init(@RequestParam("flowCode") String flowCode,
                                     @RequestParam("tableCode") String tableCode) {
        return toAjax(flowFormVersionService.initDraft(flowCode, tableCode));
    }

    /**
     * 保存草稿（仅草稿版本可保存，明细全量替换）
     */
    @Operation(summary = "保存表单配置草稿")
    @PreAuthorize(hasPermi = "flow:form:edit")
    @Log(title = "流程表单配置版本", operationType = OperationType.UPDATE)
    @PostMapping("/save")
    public CommonResult<Object> save(@RequestBody FlowFormVersionDTO dto) {
        return toAjax(flowFormVersionService.saveDraft(dto));
    }

    /**
     * 从历史版本复制出下一个版本号的草稿
     */
    @Operation(summary = "复制为新草稿")
    @PreAuthorize(hasPermi = "flow:form:add")
    @Log(title = "流程表单配置版本", operationType = OperationType.INSERT)
    @PostMapping("/copy/{id}")
    public CommonResult<Object> copy(@PathVariable("id") String id) {
        return toAjax(flowFormVersionService.copyAsDraft(id));
    }

    /**
     * 发布（置为当前生效版本）
     */
    @Operation(summary = "发布表单配置版本")
    @PreAuthorize(hasPermi = "flow:form:edit")
    @Log(title = "流程表单配置版本", operationType = OperationType.UPDATE)
    @PostMapping("/publish/{id}")
    public CommonResult<Object> publish(@PathVariable("id") String id) {
        return toAjax(flowFormVersionService.publish(id));
    }

    /**
     * 回滚（历史发布版本重新置为当前生效）
     */
    @Operation(summary = "回滚到历史发布版本")
    @PreAuthorize(hasPermi = "flow:form:edit")
    @Log(title = "流程表单配置版本", operationType = OperationType.UPDATE)
    @PostMapping("/rollback/{id}")
    public CommonResult<Object> rollback(@PathVariable("id") String id) {
        return toAjax(flowFormVersionService.rollback(id));
    }

    /**
     * 删除版本（仅草稿或非当前生效的发布版本）
     */
    @Operation(summary = "删除表单配置版本")
    @PreAuthorize(hasPermi = "flow:form:remove")
    @Log(title = "流程表单配置版本", operationType = OperationType.DELETE)
    @DeleteMapping("/{id}")
    public CommonResult<Object> remove(@PathVariable("id") String id) {
        return toAjax(flowFormVersionService.deleteVersion(id));
    }

    /**
     * 旧表数据迁移：flow_form_bind + flow_form_node_config → 表单配置发布版 v1
     */
    @Operation(summary = "迁移旧表单配置数据")
    @PreAuthorize(hasPermi = "flow:form:edit")
    @Log(title = "流程表单配置版本", operationType = OperationType.INSERT)
    @PostMapping("/migrate-from-legacy")
    public CommonResult<Integer> migrateFromLegacy() {
        return CommonResult.ok(flowFormVersionService.migrateFromLegacy());
    }
}
