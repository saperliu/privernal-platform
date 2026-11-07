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

package com.privernal.dynamic.dbs.controller;

import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormLog;
import com.privernal.dynamic.dbs.groovy.ScriptValidator;
import com.privernal.dynamic.dbs.service.IDynamicFormService;
import com.privernal.dynamic.dto.DynamicFormDTO;
import com.privernal.dynamic.filter.DynamicFormFilterDTO;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static com.privernal.common.core.constant.DataConstants.DATA_STATUS_YES;
import static com.privernal.dynamic.constants.DynamicConstants.BACK_SCRIPT;
import static com.privernal.dynamic.constants.DynamicConstants.BACK_SCRIPT_ATTR;

/**
 * 前端form配置Controller
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description 前端form配置的接口
 */
@Tag(name = "前端form配置相关接口")
@RestController
@Slf4j
public class DynamicFormController extends BaseController {
    @Autowired
    private IDynamicFormService dynamicFormService;

    @Autowired
    private com.privernal.dynamic.dbs.service.impl.DynamicFormValidateService formValidateService;

    @Autowired
    private com.privernal.dynamic.dbs.service.impl.DynamicFormImpactService formImpactService;

    @Autowired
    private com.privernal.dynamic.script.service.AiArtifactLedgerService artifactLedgerService;

    /**
     * 查询前端form配置列表
     */
    @Operation(summary = "查询前端form配置列表", description = "查询前端form配置列表")
    @PreAuthorize(hasPermi = "dynamic:form:list")
    @PostMapping("/back/dynamic/form/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody DynamicFormFilterDTO dynamicForm) {
        startPage(dynamicForm);
        List<DynamicFormDTO> list = dynamicFormService.selectDynamicFormList(dynamicForm);
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        return tableResult;
    }

    /**
     * 获取前端form配置详细信息
     */
    @Operation(summary = "获取前端form配置详细信息", description = "获取前端form配置详细信息")
    @PreAuthorize(hasPermi = "dynamic:form:query")
    @GetMapping(value = "/back/dynamic/form/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(dynamicFormService.selectDynamicFormDetailDTOById(id));
    }

    /**
     * validateDefinition 纯校验：save 前置只读检查，
     * 零副作用（不建表、不写 dynamic_form、不登记关系）。AI/用户先 validate 再 save。
     */
    @Operation(summary = "表单定义纯校验（零副作用，返回结构化错误清单）")
    @PreAuthorize(hasPermi = "dynamic:form:add")
    @PostMapping(value = "/back/dynamic/form/validate")
    public CommonResult<Object> validate(@RequestBody DynamicFormDTO dynamicFormDTO) {
        String scriptError = backScriptError(dynamicFormDTO);
        if (scriptError != null) {
            return CommonResult.ok(java.util.List.of(
                    java.util.Map.of("code", "BACK_SCRIPT_INVALID", "message", scriptError, "field", null)));
        }
        String customViewError = customViewError(dynamicFormDTO);
        if (customViewError != null) {
            return CommonResult.ok(java.util.List.of(
                    java.util.Map.of("code", "CUSTOM_VIEW_INVALID", "message", customViewError, "field", null)));
        }
        return CommonResult.ok(formValidateService.validateDefinition(dynamicFormDTO));
    }

    /**
     * 新增前端form配置
     */
    @Operation(summary = "新增前端form配置", description = "新增前端form配置")
    @PreAuthorize(hasPermi = "dynamic:form:add")
    @Log(title = "前端form配置", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/dynamic/form/save")
    public CommonResult<Object> add(@RequestBody DynamicFormDTO dynamicFormDTO) {
        String scriptError = saveError(dynamicFormDTO);
        if (scriptError != null) {
            return CommonResult.fail(scriptError);
        }
        DynamicForm dynamicForm = new DynamicForm();
        BeanUtils.copyProperties(dynamicFormDTO, dynamicForm, DataConstants.IGNORED_ON_COPY);
        dynamicForm.setStatus(DATA_STATUS_YES);
        dynamicForm = dynamicFormService.insertDynamicForm(dynamicForm);
        // B3 生成物台账：表单创建即登记（人工操作 sessionId=manual，编排层接入后传会话 id）
        try {
            artifactLedgerService.record(null, "FORM", dynamicForm.getId(), dynamicForm.getTableCode());
        } catch (Exception e) {
            logger.warn("生成物台账登记失败 form={}", dynamicForm.getId(), e);
        }
        return CommonResult.ok(dynamicForm);
    }

    /**
     * 影响面预评估：编辑/发布前人工确认关卡的数据源。
     * body 可带提议 metaData（对比列集合差异）；不带则只输出在办实例与权限码现状。
     */
    @Operation(summary = "表单影响面预评估（在办实例/DDL/删除动作/权限码）")
    @PreAuthorize(hasPermi = "dynamic:form:query")
    @PostMapping(value = "/back/dynamic/form/impact")
    public CommonResult<Object> impact(@RequestBody Map<String, Object> payload) {
        String formId = String.valueOf(payload.get("formId"));
        com.alibaba.fastjson2.JSONObject proposed = (com.alibaba.fastjson2.JSONObject) payload.get("metaData");
        return CommonResult.ok(formImpactService.impact(formId, proposed));
    }

    /**
     * 修改前端form配置
     */
    @Operation(summary = "修改前端form配置", description = "修改前端form配置")
    @PreAuthorize(hasPermi = "dynamic:form:edit")
    @Log(title = "前端form配置", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/dynamic/form/edit")
    public CommonResult<Object> edit(@RequestBody DynamicFormDTO dynamicFormDTO) {
        String scriptError = saveError(dynamicFormDTO);
        if (scriptError != null) {
            return CommonResult.fail(scriptError);
        }
        DynamicForm dynamicForm = new DynamicForm();
        BeanUtils.copyProperties(dynamicFormDTO, dynamicForm, DataConstants.IGNORED_ON_COPY);
        return CommonResult.ok(dynamicFormService.updateDynamicForm(dynamicForm));
    }

    /**
     * 校验表单元数据中配置的后台脚本语法
     *
     * @return 校验通过返回 null，否则返回带错误详情的失败消息
     */
    private String backScriptError(DynamicFormDTO dynamicFormDTO) {
        if (dynamicFormDTO == null || dynamicFormDTO.getMetaData() == null) {
            return null;
        }
        JSONObject backScriptConfig = dynamicFormDTO.getMetaData().getJSONObject(BACK_SCRIPT);
        if (backScriptConfig == null) {
            return null;
        }
        String error = ScriptValidator.validateScript(backScriptConfig.getString(BACK_SCRIPT_ATTR));
        return error == null ? null : "脚本验证失败：" + error;
    }

    /**
     * 校验 metaData.customView（自定义 Vue 页面）结构兜底：enabled 必须有源码、
     * 源码限长、必须含 template 块。与 backScriptError 同约定：通过返回 null。
     */
    private String customViewError(DynamicFormDTO dynamicFormDTO) {
        return formValidateService.customViewError(dynamicFormDTO);
    }

    /**
     * 保存/草稿链的合并定义校验：backScript 语法 + customView 结构，任一失败即拦截
     */
    private String saveError(DynamicFormDTO dynamicFormDTO) {
        String scriptError = backScriptError(dynamicFormDTO);
        if (scriptError != null) {
            return scriptError;
        }
        return customViewError(dynamicFormDTO);
    }

    /**
     * 删除前端form配置
     */
    @Operation(summary = "删除前端form配置", description = "删除前端form配置")
    @PreAuthorize(hasPermi = "dynamic:form:remove")
    @Log(title = "前端form配置", operationType = OperationType.DELETE)
    @DeleteMapping("/back/dynamic/form/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(dynamicFormService.deleteDynamicFormByIds(ids));
    }

    /**
     * 保存表单设计草稿：只写版本快照留痕，不改生效定义、不改物理表结构
     */
    @Operation(summary = "保存表单设计草稿", description = "草稿仅留痕不生效；发布走 /back/dynamic/form/publish/{logId}")
    @PreAuthorize(hasPermi = "dynamic:form:edit")
    @Log(title = "前端form配置", operationType = OperationType.UPDATE)
    @PostMapping(value = "/back/dynamic/form/draft")
    public CommonResult<Object> saveDraft(@RequestBody DynamicFormDTO dynamicFormDTO) {
        String scriptError = saveError(dynamicFormDTO);
        if (scriptError != null) {
            return CommonResult.fail(scriptError);
        }
        DynamicForm dynamicForm = new DynamicForm();
        BeanUtils.copyProperties(dynamicFormDTO, dynamicForm, DataConstants.IGNORED_ON_COPY);
        DynamicFormLog draft = dynamicFormService.saveFormDraft(dynamicForm);
        // B3 生成物台账：草稿（含新建草稿）按表单登记，会话归属由 X-AI-Session 头解析（AI 会话撤销面）
        try {
            artifactLedgerService.record(null, "FORM", draft.getFormId(), dynamicForm.getTableCode());
        } catch (Exception e) {
            logger.warn("生成物台账登记失败 form={}", draft.getFormId(), e);
        }
        return CommonResult.ok(draft);
    }

    /**
     * 查询表单最新的未发布设计草稿
     */
    @Operation(summary = "查询表单最新设计草稿", description = "无未发布草稿时 data 为 null")
    @PreAuthorize(hasPermi = "dynamic:form:query")
    @GetMapping(value = "/back/dynamic/form/draft/{formId}")
    public CommonResult<Object> findDraft(@PathVariable("formId") String formId) {
        return CommonResult.ok(dynamicFormService.findFormDraft(formId));
    }

    /**
     * 发布指定版本快照：快照内容应用为生效定义（改表、重建关联登记），草稿发布后清除
     */
    @Operation(summary = "发布指定版本快照", description = "DRAFT/VERSION 快照均可发布；发布写入新 VERSION 快照")
    @PreAuthorize(hasPermi = "dynamic:form:edit")
    @Log(title = "前端form配置", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/dynamic/form/publish/{logId}")
    public CommonResult<Object> publish(@PathVariable("logId") String logId) {
        return CommonResult.ok(dynamicFormService.publishFormLog(logId));
    }

    /**
     * 回滚到指定正式版本：历史 VERSION 快照重新应用为生效定义，版本号继续递增
     */
    @Operation(summary = "回滚到指定正式版本", description = "仅 VERSION 快照可回滚；草稿请走发布")
    @PreAuthorize(hasPermi = "dynamic:form:edit")
    @Log(title = "前端form配置", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/dynamic/form/rollback/{logId}")
    public CommonResult<Object> rollback(@PathVariable("logId") String logId) {
        return CommonResult.ok(dynamicFormService.rollbackFormLog(logId));
    }

    /**
     * 获取前端form配置详细信息
     */
    @Operation(summary = "获取前端form配置详细信息", description = "获取前端form配置详细信息")
    @PreAuthorize(hasPermi = "dynamic:form:query")
    @GetMapping(value = "/back/dynamic/form/code/{code}")
    public CommonResult<Object> findFormByCode(@PathVariable("code") String code) {
        return CommonResult.ok(dynamicFormService.selectDynamicFormByTableCode(code));
    }

}
