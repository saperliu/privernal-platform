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

package com.privernal.dynamic.script.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.script.domain.DynamicScriptAsset;
import com.privernal.dynamic.script.domain.DynamicScriptBinding;
import com.privernal.dynamic.script.domain.DynamicScriptExecLog;
import com.privernal.dynamic.script.engine.ScriptAssetExecutor;
import com.privernal.dynamic.script.mapper.DynamicScriptAssetMapper;
import com.privernal.dynamic.script.mapper.DynamicScriptBindingMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 脚本挂点绑定服务。绑定落独立表 {@code dynamic_script_binding}
 * （设计器保存链整体覆盖 metaData，挂点配置绝不能进 metaData）。
 * <p>
 * 主挂点 SAVE_VALIDATE：保存链在 backScript 之前统一执行，任一绑定失败抛业务异常
 * 中止保存并回显 fail_message；参数映射解析顺序 {{…}} 上下文变量 → 表单字段 → 常量。
 */
@Slf4j
@Service
public class ScriptBindingService {

    private static final Set<String> BIND_POINTS = Set.of(
            DynamicScriptBinding.POINT_SAVE_VALIDATE, DynamicScriptBinding.POINT_BACK_SCRIPT,
            DynamicScriptBinding.POINT_SCHEDULE, DynamicScriptBinding.POINT_LINKAGE_SCRIPT);

    @Autowired
    private DynamicScriptBindingMapper bindingMapper;

    @Autowired
    private DynamicScriptAssetMapper assetMapper;

    @Autowired
    private ScriptAssetExecutor scriptAssetExecutor;

    public List<DynamicScriptBinding> listByForm(String formId) {
        return bindingMapper.selectList(Wrappers.<DynamicScriptBinding>lambdaQuery()
                .eq(DynamicScriptBinding::getFormId, formId)
                .orderByAsc(DynamicScriptBinding::getExecutionOrder)
                .orderByAsc(DynamicScriptBinding::getCreatedTime));
    }

    /** 批量保存某表单的挂点绑定（整表替换语义，与设计器/高级设置面板的整单提交对齐） */
    @Transactional(rollbackFor = Exception.class)
    public void saveForForm(String formId, String tableCode, List<DynamicScriptBinding> bindings) {
        if (StringUtils.isEmpty(formId)) {
            throw new ServiceException("formId 不能为空");
        }
        bindingMapper.delete(Wrappers.<DynamicScriptBinding>lambdaQuery()
                .eq(DynamicScriptBinding::getFormId, formId));
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        int order = 0;
        for (DynamicScriptBinding binding : bindings) {
            validateBinding(binding);
            if (binding.getExecutionOrder() == null) {
                binding.setExecutionOrder(++order);
            }
            binding.setId(null);
            binding.setFormId(formId);
            if (StringUtils.isEmpty(binding.getTableCode())) {
                binding.setTableCode(tableCode);
            }
            binding.setOrganizationId(SecurityUtils.getOrganizationId());
            binding.setDepartmentId(SecurityUtils.getDepartmentId());
            bindingMapper.insert(binding);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeEnabled(String id, String enabled) {
        DynamicScriptBinding existing = requireBinding(id);
        DynamicScriptBinding update = new DynamicScriptBinding();
        update.setId(id);
        update.setEnabled("Y".equals(enabled) ? "Y" : "N");
        bindingMapper.updateById(update);
        log.debug("挂点绑定启停 id={} {} -> {}", id, existing.getEnabled(), update.getEnabled());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        requireBinding(id);
        bindingMapper.deleteById(id);
    }

    // ---------------- 保存校验挂点（主路径） ----------------

    /**
     * 表单保存链统一入口：按 execution_order 逐条跑 SAVE_VALIDATE 绑定，
     * 任一 BOOLEAN 结果为 false 或执行异常 → 抛业务异常（fail_message 优先）中止保存。
     * 本方法零写库（执行日志除外），事务语义交给外层保存链。
     */
    public void runSaveValidations(String formId, String tableCode, Map<String, Object> data) {
        List<DynamicScriptBinding> bindings = bindingMapper.selectList(
                Wrappers.<DynamicScriptBinding>lambdaQuery()
                        .eq(DynamicScriptBinding::getFormId, formId)
                        .eq(DynamicScriptBinding::getBindPoint, DynamicScriptBinding.POINT_SAVE_VALIDATE)
                        .eq(DynamicScriptBinding::getEnabled, "Y")
                        .orderByAsc(DynamicScriptBinding::getExecutionOrder));
        if (bindings.isEmpty()) {
            return;
        }
        for (DynamicScriptBinding binding : bindings) {
            DynamicScriptAsset asset = assetMapper.selectById(binding.getAssetId());
            if (asset == null) {
                throw new ServiceException("挂点绑定的脚本资产不存在（绑定 " + binding.getId() + "），请联系管理员");
            }
            Map<String, Object> params = resolveParams(binding, data);
            Object result;
            try {
                result = scriptAssetExecutor.runByCode(asset.getScriptCode(), params,
                        DynamicScriptExecLog.POINT_SAVE_VALIDATE,
                        formId, tableCode, data == null ? null : String.valueOf(data.get("id")));
            } catch (ServiceException e) {
                throw new ServiceException(failMessage(binding, e.getMessage()));
            }
            if (DynamicScriptAsset.RESULT_BOOLEAN.equals(asset.getResultType())
                    && !Boolean.TRUE.equals(result)) {
                throw new ServiceException(failMessage(binding, null));
            }
        }
    }

    // ---------------- 内部 ----------------

    private void validateBinding(DynamicScriptBinding binding) {
        if (binding.getAssetId() == null || binding.getAssetId().isEmpty()) {
            throw new ServiceException("绑定缺少 assetId");
        }
        DynamicScriptAsset asset = assetMapper.selectById(binding.getAssetId());
        if (asset == null) {
            throw new ServiceException("绑定的脚本资产不存在：" + binding.getAssetId());
        }
        if (!DynamicScriptAsset.STATUS_PUBLISHED.equals(asset.getStatus())) {
            throw new ServiceException("绑定的脚本资产未发布：" + asset.getScriptCode());
        }
        if (!BIND_POINTS.contains(binding.getBindPoint())) {
            throw new ServiceException("bindPoint 必须为 SAVE_VALIDATE/BACK_SCRIPT/SCHEDULE/LINKAGE_SCRIPT");
        }
        parseParamMapping(binding.getParamMapping());
        if (binding.getFailMessage() != null && binding.getFailMessage().length() > 200) {
            throw new ServiceException("failMessage 上限 200 字符");
        }
    }

    private JSONObject parseParamMapping(String paramMapping) {
        if (StringUtils.isEmpty(paramMapping)) {
            return new JSONObject();
        }
        JSONObject mapping = JSON.parseObject(paramMapping);
        if (mapping == null) {
            throw new ServiceException("param_mapping 必须是 JSON 对象");
        }
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            if (entry.getValue() != null && entry.getValue().toString().length() > 500) {
                throw new ServiceException("param_mapping 值过长（" + entry.getKey() + "）");
            }
        }
        return mapping;
    }

    /** 参数解析：{{…}} 上下文变量 → data 中存在的字段编码 → 常量 */
    private Map<String, Object> resolveParams(DynamicScriptBinding binding, Map<String, Object> data) {
        JSONObject mapping = parseParamMapping(binding.getParamMapping());
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            String raw = entry.getValue() == null ? null : entry.getValue().toString().trim();
            params.put(entry.getKey(), resolveValue(raw, data));
        }
        return scriptAssetExecutor.coerceParams(assetFor(binding), params);
    }

    private Object resolveValue(String raw, Map<String, Object> data) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if ("{{currentUser}}".equals(raw)) {
            return safeUserId();
        }
        if ("{{now}}".equals(raw)) {
            return new Date();
        }
        if ("{{orgId}}".equals(raw)) {
            return OrganizationContextHolder.getOrganizationId();
        }
        if (raw.startsWith("{{") && raw.endsWith("}}")) {
            throw new ServiceException("不支持的上下文变量：" + raw + "（支持 {{currentUser}}/{{now}}/{{orgId}}）");
        }
        if (data != null && data.containsKey(raw)) {
            return data.get(raw);
        }
        return raw;
    }

    private DynamicScriptAsset assetFor(DynamicScriptBinding binding) {
        DynamicScriptAsset asset = assetMapper.selectById(binding.getAssetId());
        if (asset == null) {
            throw new ServiceException("绑定的脚本资产不存在：" + binding.getAssetId());
        }
        return asset;
    }

    private String failMessage(DynamicScriptBinding binding, String detail) {
        if (StringUtils.isNotEmpty(binding.getFailMessage())) {
            return binding.getFailMessage();
        }
        return "保存校验未通过" + (detail == null ? "" : "：" + detail);
    }

    private DynamicScriptBinding requireBinding(String id) {
        DynamicScriptBinding binding = bindingMapper.selectById(id);
        if (binding == null) {
            throw new ServiceException("挂点绑定不存在：" + id);
        }
        return binding;
    }

    private String safeUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
