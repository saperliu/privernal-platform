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
import com.privernal.dynamic.script.domain.DynamicScriptAsset;
import com.privernal.dynamic.script.domain.DynamicScriptAssetLog;
import com.privernal.dynamic.script.domain.DynamicScriptBinding;
import com.privernal.dynamic.script.domain.DynamicScriptExecLog;
import com.privernal.dynamic.script.engine.ScriptAssetExecutor;
import com.privernal.dynamic.script.engine.ScriptAssetSandboxValidator;
import com.privernal.dynamic.script.mapper.DynamicScriptAssetLogMapper;
import com.privernal.dynamic.script.mapper.DynamicScriptAssetMapper;
import com.privernal.dynamic.script.mapper.DynamicScriptBindingMapper;
import com.privernal.dynamic.script.mapper.DynamicScriptExecLogMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
 * 脚本资产服务：CRUD + 版本语义 + 发布纪律 + 试运行。
 * <p>
 * 版本语义（对齐流程定义，plan §3-C1）：DRAFT 带 id 覆盖同版本；PUBLISHED 改 body 或
 * 签名（params_schema 的名字/类型/必填、result_type）必须递增版本并回到 DRAFT；
 * 发布前必须对该版本试运行成功一次（执行日志 bind_point=TRIAL 最近一行 success=Y）。
 */
@Slf4j
@Service
public class ScriptAssetService {

    private static final Set<String> CATEGORIES = Set.of(
            DynamicScriptAsset.CATEGORY_VALIDATION, DynamicScriptAsset.CATEGORY_CALC,
            DynamicScriptAsset.CATEGORY_AUTOFILL, DynamicScriptAsset.CATEGORY_DATA_OP);
    private static final Set<String> RESULT_TYPES = Set.of(
            DynamicScriptAsset.RESULT_NONE, DynamicScriptAsset.RESULT_BOOLEAN,
            DynamicScriptAsset.RESULT_OBJECT);

    @Autowired
    private DynamicScriptAssetMapper assetMapper;

    @Autowired
    private DynamicScriptAssetLogMapper assetLogMapper;

    @Autowired
    private DynamicScriptExecLogMapper execLogMapper;

    @Autowired
    private DynamicScriptBindingMapper bindingMapper;

    @Autowired
    private ScriptAssetSandboxValidator sandboxValidator;

    @Autowired
    private ScriptAssetExecutor scriptAssetExecutor;

    public Page<DynamicScriptAsset> page(long pageNum, long pageSize, String keyword, String category,
                                         String status, String appId) {
        var wrapper = Wrappers.<DynamicScriptAsset>lambdaQuery();
        if (StringUtils.isNotEmpty(keyword)) {
            wrapper.and(w -> w.like(DynamicScriptAsset::getScriptCode, keyword)
                    .or().like(DynamicScriptAsset::getScriptName, keyword));
        }
        wrapper.eq(StringUtils.isNotEmpty(category), DynamicScriptAsset::getCategory, category)
                .eq(StringUtils.isNotEmpty(status), DynamicScriptAsset::getStatus, status)
                .eq(StringUtils.isNotEmpty(appId), DynamicScriptAsset::getAppId, appId)
                .orderByDesc(DynamicScriptAsset::getCreatedTime);
        return assetMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public DynamicScriptAsset requireAsset(String id) {
        DynamicScriptAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new ServiceException("脚本资产不存在：" + id);
        }
        return asset;
    }

    @Transactional(rollbackFor = Exception.class)
    public DynamicScriptAsset create(DynamicScriptAsset asset) {
        normalizeAndValidate(asset);
        if (countByCode(asset.getScriptCode()) > 0) {
            throw new ServiceException("脚本编码已存在：" + asset.getScriptCode());
        }
        sandboxValidator.validate(asset.getBody());
        asset.setId(null);
        asset.setVersion(1);
        asset.setStatus(DynamicScriptAsset.STATUS_DRAFT);
        asset.setOrganizationId(SecurityUtils.getOrganizationId());
        asset.setDepartmentId(SecurityUtils.getDepartmentId());
        assetMapper.insert(asset);
        return asset;
    }

    @Transactional(rollbackFor = Exception.class)
    public DynamicScriptAsset update(DynamicScriptAsset patch) {
        DynamicScriptAsset existing = requireAsset(patch.getId());
        normalizeAndValidate(patch);
        if (DynamicScriptAsset.STATUS_DISABLED.equals(existing.getStatus())) {
            throw new ServiceException("已停用资产不可编辑，请先启用");
        }
        sandboxValidator.validate(patch.getBody());
        boolean published = DynamicScriptAsset.STATUS_PUBLISHED.equals(existing.getStatus());
        boolean signatureChanged = published && signatureChanged(existing, patch);
        DynamicScriptAsset update = new DynamicScriptAsset();
        update.setId(existing.getId());
        update.setScriptName(patch.getScriptName());
        update.setCategory(patch.getCategory());
        update.setBody(patch.getBody());
        update.setParamsSchema(patch.getParamsSchema());
        update.setResultType(patch.getResultType());
        update.setTimeoutSeconds(patch.getTimeoutSeconds());
        update.setAppId(patch.getAppId());
        if (published && (signatureChanged || !patch.getBody().equals(existing.getBody()))) {
            // 已发布版本不可原地改：签名或正文变化 → 递增版本、回到草稿，需重新试运行 + 发布
            update.setVersion(existing.getVersion() + 1);
            update.setStatus(DynamicScriptAsset.STATUS_DRAFT);
        }
        assetMapper.updateById(update);
        return requireAsset(existing.getId());
    }

    /** 发布：必须 DRAFT 且该版本试运行成功过一次；落整行快照 */
    @Transactional(rollbackFor = Exception.class)
    public void publish(String id) {
        DynamicScriptAsset asset = requireAsset(id);
        if (!DynamicScriptAsset.STATUS_DRAFT.equals(asset.getStatus())) {
            throw new ServiceException("仅草稿状态可发布（当前：" + asset.getStatus() + "）");
        }
        if (!hasSuccessfulTrial(asset.getId(), asset.getVersion())) {
            throw new ServiceException("发布前必须对该版本试运行成功一次（先在试运行面板实跑）");
        }
        DynamicScriptAsset update = new DynamicScriptAsset();
        update.setId(id);
        update.setStatus(DynamicScriptAsset.STATUS_PUBLISHED);
        assetMapper.updateById(update);
        DynamicScriptAssetLog logRow = new DynamicScriptAssetLog();
        logRow.setAssetId(asset.getId());
        logRow.setScriptCode(asset.getScriptCode());
        logRow.setVersion(asset.getVersion());
        logRow.setLogType(DynamicScriptAssetLog.LOG_PUBLISH);
        JSONObject snapshot = (JSONObject) JSON.toJSON(requireAsset(id));
        snapshot.put("publishedBy", safeUserId());
        snapshot.put("publishedTime", new Date());
        logRow.setSnapshot(snapshot.toJSONString());
        logRow.setOrganizationId(asset.getOrganizationId());
        assetLogMapper.insert(logRow);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String id, String status) {
        DynamicScriptAsset existing = requireAsset(id);
        if (!DynamicScriptAsset.STATUS_DISABLED.equals(status)
                && !DynamicScriptAsset.STATUS_PUBLISHED.equals(status)) {
            throw new ServiceException("状态只允许在 PUBLISHED/DISABLED 间切换");
        }
        if (DynamicScriptAsset.STATUS_DISABLED.equals(status)
                && !DynamicScriptAsset.STATUS_PUBLISHED.equals(existing.getStatus())) {
            throw new ServiceException("仅已发布资产可停用");
        }
        if (DynamicScriptAsset.STATUS_PUBLISHED.equals(status)
                && DynamicScriptAsset.STATUS_DISABLED.equals(existing.getStatus())) {
            // 重新启用：回 PUBLISHED 前要求该版本仍有试运行成功记录（版本未变时通常已有）
            if (!hasSuccessfulTrial(existing.getId(), existing.getVersion())) {
                throw new ServiceException("重新启用前必须试运行成功一次");
            }
        }
        DynamicScriptAsset update = new DynamicScriptAsset();
        update.setId(id);
        update.setStatus(status);
        assetMapper.updateById(update);
        if (DynamicScriptAsset.STATUS_DISABLED.equals(status)) {
            DynamicScriptAssetLog logRow = new DynamicScriptAssetLog();
            logRow.setAssetId(id);
            logRow.setScriptCode(existing.getScriptCode());
            logRow.setVersion(existing.getVersion());
            logRow.setLogType(DynamicScriptAssetLog.LOG_DISABLE);
            logRow.setOrganizationId(existing.getOrganizationId());
            assetLogMapper.insert(logRow);
        }
    }

    /** 删除前查挂点引用（复刻 ColumnReferenceGuard 的"先查引用再删"纪律） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        DynamicScriptAsset existing = requireAsset(id);
        Long refs = bindingMapper.selectCount(Wrappers.<DynamicScriptBinding>lambdaQuery()
                .eq(DynamicScriptBinding::getAssetId, id));
        if (refs != null && refs > 0) {
            throw new ServiceException("资产被 " + refs + " 条挂点绑定引用，请先解除绑定（引用影响面见引用查询接口）");
        }
        assetMapper.deleteById(id);
    }

    /** 引用影响面：资产被哪些表单/挂点引用（删除/改签名前必查） */
    public List<DynamicScriptBinding> references(String assetId) {
        return bindingMapper.selectList(Wrappers.<DynamicScriptBinding>lambdaQuery()
                .eq(DynamicScriptBinding::getAssetId, assetId)
                .orderByAsc(DynamicScriptBinding::getFormId));
    }

    /** 试运行：填 params JSON → 沙箱实跑 → result/log（发布前必须成功一次的纪律来源） */
    public Map<String, Object> trial(String id, Map<String, Object> params) {
        DynamicScriptAsset asset = requireAsset(id);
        Map<String, Object> coerced = scriptAssetExecutor.coerceParams(asset, params);
        Object result = scriptAssetExecutor.runVersion(asset, asset.getVersion(), coerced,
                DynamicScriptExecLog.POINT_TRIAL, null);
        Map<String, Object> out = new HashMap<>();
        out.put("result", result);
        out.put("version", asset.getVersion());
        out.put("trialPassed", true);
        return out;
    }

    /** 按编码直跑当前发布版（backScript 迁移期 scriptAssets.invoke 的服务端对等入口） */
    public Object runByCode(String scriptCode, Map<String, Object> params) {
        return scriptAssetExecutor.runByCode(scriptCode, params,
                DynamicScriptExecLog.POINT_BACK_SCRIPT, null, null, null);
    }

    public List<DynamicScriptExecLog> execLogs(String assetId, long pageNum, long pageSize) {
        return execLogMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<DynamicScriptExecLog>lambdaQuery()
                        .eq(DynamicScriptExecLog::getAssetId, assetId)
                        .orderByDesc(DynamicScriptExecLog::getCreatedTime)).getRecords();
    }

    public List<DynamicScriptAssetLog> versionHistory(String assetId) {
        return assetLogMapper.selectList(Wrappers.<DynamicScriptAssetLog>lambdaQuery()
                .eq(DynamicScriptAssetLog::getAssetId, assetId)
                .orderByDesc(DynamicScriptAssetLog::getCreatedTime));
    }

    // ---------------- 内部 ----------------

    private void normalizeAndValidate(DynamicScriptAsset asset) {
        if (StringUtils.isEmpty(asset.getScriptCode())) {
            throw new ServiceException("scriptCode 不能为空");
        }
        if (!asset.getScriptCode().matches("[a-z][a-z0-9_]{1,62}")) {
            throw new ServiceException("scriptCode 必须以小写字母开头，仅含小写字母/数字/下划线（2-63 位）");
        }
        if (StringUtils.isEmpty(asset.getScriptName())) {
            throw new ServiceException("scriptName 不能为空");
        }
        if (!DynamicScriptAsset.LANGUAGE_GROOVY.equals(asset.getLanguage())) {
            asset.setLanguage(DynamicScriptAsset.LANGUAGE_GROOVY);
        }
        if (!CATEGORIES.contains(asset.getCategory())) {
            throw new ServiceException("category 必须为 VALIDATION/CALC/AUTOFILL/DATA_OP");
        }
        if (!RESULT_TYPES.contains(asset.getResultType())) {
            throw new ServiceException("resultType 必须为 NONE/BOOLEAN/OBJECT");
        }
        if (asset.getTimeoutSeconds() == null || asset.getTimeoutSeconds() <= 0) {
            asset.setTimeoutSeconds(10);
        }
        if (asset.getTimeoutSeconds() > 60) {
            throw new ServiceException("timeout_seconds 上限 60 秒");
        }
        validateParamsSchema(asset.getParamsSchema());
        if (StringUtils.isEmpty(asset.getBody())) {
            throw new ServiceException("脚本正文不能为空");
        }
    }

    private void validateParamsSchema(String paramsSchema) {
        if (StringUtils.isEmpty(paramsSchema)) {
            return;
        }
        JSONArray schema = ScriptAssetExecutor.parseParamsSchema(paramsSchema);
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < schema.size(); i++) {
            JSONObject def = schema.getJSONObject(i);
            String name = def.getString("name");
            if (name == null || !name.matches("[a-zA-Z][a-zA-Z0-9_]{0,31}")) {
                throw new ServiceException("params_schema 第 " + (i + 1) + " 项 name 非法（字母开头，≤32 位标识符）");
            }
            if (!seen.add(name)) {
                throw new ServiceException("params_schema 参数名重复：" + name);
            }
            String type = def.getString("type");
            if (type != null && !Set.of("STRING", "NUMBER", "DATE", "BOOLEAN", "JSON").contains(type)) {
                throw new ServiceException("params_schema 参数 " + name + " 的 type 非法（STRING/NUMBER/DATE/BOOLEAN/JSON）");
            }
        }
    }

    /** 签名 = 参数（名字/类型/required）+ 结果类型；任一变化即视为改签名 */
    private boolean signatureChanged(DynamicScriptAsset a, DynamicScriptAsset b) {
        return !canonicalSignature(a).equals(canonicalSignature(b));
    }

    private String canonicalSignature(DynamicScriptAsset asset) {
        JSONArray schema = ScriptAssetExecutor.parseParamsSchema(asset.getParamsSchema());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < schema.size(); i++) {
            JSONObject def = schema.getJSONObject(i);
            sb.append(def.getString("name")).append(':')
                    .append(def.getString("type")).append(':')
                    .append(Boolean.TRUE.equals(def.getBoolean("required"))).append(';');
        }
        sb.append("->").append(asset.getResultType());
        return sb.toString();
    }

    private boolean hasSuccessfulTrial(String assetId, Integer version) {
        List<DynamicScriptExecLog> logs = execLogMapper.selectPage(new Page<>(1, 1, false),
                Wrappers.<DynamicScriptExecLog>lambdaQuery()
                        .eq(DynamicScriptExecLog::getAssetId, assetId)
                        .eq(DynamicScriptExecLog::getVersion, version)
                        .eq(DynamicScriptExecLog::getBindPoint, DynamicScriptExecLog.POINT_TRIAL)
                        .eq(DynamicScriptExecLog::getSuccess, "Y")
                        .orderByDesc(DynamicScriptExecLog::getCreatedTime)).getRecords();
        return !logs.isEmpty();
    }

    private long countByCode(String scriptCode) {
        var wrapper = Wrappers.<DynamicScriptAsset>lambdaQuery()
                .eq(DynamicScriptAsset::getScriptCode, scriptCode);
        Long count = assetMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    private String safeUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
