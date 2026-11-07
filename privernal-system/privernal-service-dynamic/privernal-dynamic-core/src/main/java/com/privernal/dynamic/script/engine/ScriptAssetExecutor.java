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

package com.privernal.dynamic.script.engine;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dbs.groovy.GroovyScriptService;
import com.privernal.dynamic.dbs.groovy.ScriptConcurrencyGuard;
import com.privernal.dynamic.script.domain.DynamicScriptAsset;
import com.privernal.dynamic.script.domain.DynamicScriptAssetLog;
import com.privernal.dynamic.script.domain.DynamicScriptExecLog;
import com.privernal.dynamic.script.mapper.DynamicScriptAssetLogMapper;
import com.privernal.dynamic.script.mapper.DynamicScriptAssetMapper;
import com.privernal.dynamic.script.mapper.DynamicScriptExecLogMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脚本资产统一运行入口。
 * <p>
 * 安全核心口径：<b>资产运行一律走沙箱</b>（{@code GroovySandboxConfig.sandboxed()}，无豁免开关）；
 * fail-closed 的 {@code dynamic.script.sandbox.enforced} 只管存量 backScript 的灰度切换。
 * 在既有沙箱能力之上净新增四项：① 输出长度上限；② 每组织并发配额（复用
 * {@link ScriptConcurrencyGuard}）；③ 编译缓存键 (asset_id, version)；④ 试运行/发布纪律。
 * <p>
 * 已知局限（如实带入验收）：超时是协作式中断（ThreadInterrupt AST），脚本阻塞在 IO 或
 * native 调用里断不干净——资产脚本禁阻塞 IO，由白名单 import 兜底。
 */
@Slf4j
@Service
public class ScriptAssetExecutor {

    /** 单次运行输出上限（字符），防止超大结果拖垮调用链 */
    @Value("${privernal.dynamic.script.output-limit-chars:20000}")
    private int outputLimitChars;

    /** 资产超时硬上限（秒），与 plan §3-C1 timeout_seconds(≤60) 对齐 */
    private static final long MAX_ASSET_TIMEOUT_SECONDS = 60;

    @Autowired
    private GroovyScriptService groovyScriptService;

    @Autowired
    private ScriptConcurrencyGuard concurrencyGuard;

    @Autowired
    private DynamicScriptAssetMapper assetMapper;

    @Autowired
    private DynamicScriptAssetLogMapper assetLogMapper;

    @Autowired
    private DynamicScriptExecLogMapper execLogMapper;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    /** 按组织隔离的资产互调门面（绑定变量 scriptAssets，深度 ≤3 + 环路检测） */
    public ScriptAssetInvoker newInvoker() {
        return new ScriptAssetInvoker(this);
    }

    /**
     * 按资产编码运行当前发布版（挂点/互调路径）。
     *
     * @return 运行结果（BOOLEAN → Boolean；OBJECT → JSONObject/基础类型；NONE → null）
     */
    public Object runByCode(String scriptCode, Map<String, Object> params, String bindPoint,
                            String formId, String tableCode, String dataId) {
        String orgId = OrganizationContextHolder.getOrganizationId();
        var wrapper = Wrappers.<DynamicScriptAsset>lambdaQuery()
                .eq(DynamicScriptAsset::getScriptCode, scriptCode)
                .eq(DynamicScriptAsset::getStatus, DynamicScriptAsset.STATUS_PUBLISHED)
                .orderByDesc(DynamicScriptAsset::getVersion);
        if (StringUtils.isNotEmpty(orgId)) {
            wrapper.eq(DynamicScriptAsset::getOrganizationId, orgId);
        }
        DynamicScriptAsset asset = assetMapper.selectPage(new Page<>(1, 1, false), wrapper).getRecords()
                .stream().findFirst().orElse(null);
        if (asset == null) {
            throw new ServiceException("脚本资产不存在或未发布：" + scriptCode);
        }
        return doRun(asset, asset.getVersion(), params, bindPoint, formId, tableCode, dataId, false);
    }

    /**
     * 试运行/指定版本运行（管理端）。trial=true 时不要求 PUBLISHED 状态。
     */
    public Object runVersion(DynamicScriptAsset asset, Integer version, Map<String, Object> params,
                             String bindPoint, String dataId) {
        return doRun(asset, version, params, bindPoint, asset.getId(), null, dataId, true);
    }

    private Object doRun(DynamicScriptAsset asset, Integer version, Map<String, Object> params,
                         String bindPoint, String formId, String tableCode, String dataId,
                         boolean allowNonPublished) {
        if (!allowNonPublished && !DynamicScriptAsset.STATUS_PUBLISHED.equals(asset.getStatus())) {
            throw new ServiceException("脚本资产未发布：" + asset.getScriptCode());
        }
        Map<String, Object> variables = buildVariables(params);
        long effectiveTimeout = normalizeTimeout(asset.getTimeoutSeconds());
        long start = System.currentTimeMillis();
        Object result;
        String error = null;
        // 脚本执行期放行数据范围切面（与联动引擎同款先例）：无会话/系统线程里切面会把
        // daoService 查询兜底改写为 WHERE 1=4（实测踩坑）。脚本按"平台可信代码"运行，
        // 跨部门数据可见（排课冲突检查需要全组织视角），审计由 exec_log 兜底。
        // 用 merge 语义 set-then-restore，不破坏调用方线程上已有的 ignore 标志。
        Boolean prevIgnoreOrg = com.privernal.common.datascope.aspect.DataScopeHelper.getIgnoreOrganization();
        Boolean prevIgnoreDp = com.privernal.common.datascope.aspect.DataScopeHelper.getIgnoreDataPermission();
        try {
            com.privernal.common.datascope.aspect.DataScopeHelper.mergeIgnoreStrategy(true, true);
            result = concurrencyGuard.runWithQuota(() -> {
                Script compiled = compile(asset, version);
                return groovyScriptService.runCompiled(compiled.getClass(), variables, effectiveTimeout);
            });
        } catch (Exception e) {
            error = rootMessage(e);
            writeExecLog(asset, version, bindPoint, formId, tableCode, dataId, "N", null, error,
                    System.currentTimeMillis() - start);
            if (e instanceof ServiceException se) {
                throw se;
            }
            throw new ServiceException("脚本资产运行失败：" + asset.getScriptCode() + " → " + error);
        } finally {
            com.privernal.common.datascope.aspect.DataScopeHelper.mergeIgnoreStrategy(prevIgnoreOrg, prevIgnoreDp);
        }
        long elapsed = System.currentTimeMillis() - start;
        String output = stringifyResult(asset, result);
        writeExecLog(asset, version, bindPoint, formId, tableCode, dataId, "Y", output, null, elapsed);
        return result;
    }

    /** 编译缓存键 (asset_id, version)：草稿体可原地覆盖而版本不变，按文本缓存会让版本语义串味（M0 净新增③） */
    private Script compile(DynamicScriptAsset asset, Integer version) {
        String body = resolveBody(asset, version);
        Class<? extends Script> clazz = groovyScriptService.compileSandboxedCached(
                asset.getId() + ":" + version, body);
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ServiceException("脚本资产编译产物实例化失败：" + asset.getScriptCode());
        }
    }

    /** 绑定钉住历史版本时从发布快照回溯 body；当前版本直接用资产行 */
    public String resolveBody(DynamicScriptAsset asset, Integer version) {
        if (version == null || version.equals(asset.getVersion())) {
            return asset.getBody();
        }
        var snapshotLogs = assetLogMapper.selectList(Wrappers.<DynamicScriptAssetLog>lambdaQuery()
                .eq(DynamicScriptAssetLog::getAssetId, asset.getId())
                .eq(DynamicScriptAssetLog::getLogType, DynamicScriptAssetLog.LOG_PUBLISH)
                .orderByDesc(DynamicScriptAssetLog::getCreatedTime));
        for (DynamicScriptAssetLog row : snapshotLogs) {
            JSONObject snap = JSON.parseObject(row.getSnapshot());
            if (snap != null && Integer.valueOf(snap.getIntValue("version")).equals(version)) {
                return snap.getString("body");
            }
        }
        throw new ServiceException("脚本资产历史版本快照不存在：" + asset.getScriptCode() + " v" + version);
    }

    private Map<String, Object> buildVariables(Map<String, Object> params) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("daoService", applicationContext.getBean(
                com.privernal.dynamic.dbs.groovy.env.DaoService.class));
        variables.put("params", params == null ? Map.of() : params);
        variables.put("scriptAssets", newInvoker());
        variables.put("currentUser", safeUserId());
        variables.put("currentDept", safeDeptId());
        variables.put("now", new Date());
        return variables;
    }

    private long normalizeTimeout(Integer timeoutSeconds) {
        long timeout = timeoutSeconds == null || timeoutSeconds <= 0 ? 10 : timeoutSeconds;
        return Math.min(timeout, MAX_ASSET_TIMEOUT_SECONDS);
    }

    /** 按结果类型收口输出（M0 净新增①）：超限直接报错，不静默截断业务结果 */
    private String stringifyResult(DynamicScriptAsset asset, Object result) {
        if (result == null || DynamicScriptAsset.RESULT_NONE.equals(asset.getResultType())) {
            return "";
        }
        String text;
        if (result instanceof String s) {
            text = s;
        } else if (result instanceof Number || result instanceof Boolean || result instanceof Date) {
            text = String.valueOf(result);
        } else {
            text = JSON.toJSONString(result);
        }
        if (outputLimitChars > 0 && text.length() > outputLimitChars) {
            throw new ServiceException("脚本输出超过上限（" + outputLimitChars + " 字符），请调整脚本返回值");
        }
        return text;
    }

    private void writeExecLog(DynamicScriptAsset asset, Integer version, String bindPoint, String formId,
                              String tableCode, String dataId, String success, String resultSummary,
                              String errorMessage, long elapsedMs) {
        try {
            DynamicScriptExecLog row = new DynamicScriptExecLog();
            row.setAssetId(asset.getId());
            row.setScriptCode(asset.getScriptCode());
            row.setVersion(version);
            row.setBindPoint(bindPoint);
            row.setFormId(formId);
            row.setTableCode(tableCode);
            row.setDataId(dataId);
            row.setSuccess(success);
            row.setResultSummary(resultSummary == null ? null
                    : resultSummary.substring(0, Math.min(resultSummary.length(), 500)));
            row.setErrorMessage(errorMessage == null ? null
                    : errorMessage.substring(0, Math.min(errorMessage.length(), 1000)));
            row.setElapsedMs(elapsedMs);
            row.setOrganizationId(OrganizationContextHolder.getOrganizationId());
            row.setCreatedTime(new Date());
            execLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("脚本运行日志落库失败 asset={} error={}", asset.getScriptCode(), e.getMessage());
        }
    }

    private String safeUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String safeDeptId() {
        try {
            return SecurityUtils.getDepartmentId();
        } catch (Exception e) {
            return null;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    /** 解析参数契约（params_schema JSON 数组），供保存校验与运行前必填校验共用 */
    public static JSONArray parseParamsSchema(String paramsSchema) {
        if (StringUtils.isEmpty(paramsSchema)) {
            return new JSONArray();
        }
        JSONArray schema = JSON.parseArray(paramsSchema);
        return schema == null ? new JSONArray() : schema;
    }

    /** 按契约对入参做必填与类型best-effort校验/规整；未知参数原样保留 */
    public Map<String, Object> coerceParams(DynamicScriptAsset asset, Map<String, Object> raw) {
        Map<String, Object> coerced = new LinkedHashMap<>();
        Map<String, Object> input = raw == null ? Map.of() : raw;
        JSONArray schema = parseParamsSchema(asset.getParamsSchema());
        for (int i = 0; i < schema.size(); i++) {
            JSONObject def = schema.getJSONObject(i);
            String name = def.getString("name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            String type = def.getString("type");
            boolean required = Boolean.TRUE.equals(def.getBoolean("required"));
            Object value = input.containsKey(name) ? input.get(name) : def.get("default");
            if (value == null || "".equals(value)) {
                if (required) {
                    throw new ServiceException("参数缺失：" + name + "（资产 " + asset.getScriptCode() + "）");
                }
                coerced.put(name, null);
                continue;
            }
            coerced.put(name, coerceValue(name, type, value));
        }
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            coerced.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return coerced;
    }

    private Object coerceValue(String name, String type, Object value) {
        try {
            if (type == null) {
                return value;
            }
            return switch (type) {
                case "STRING" -> String.valueOf(value);
                case "NUMBER" -> value instanceof Number ? value : new java.math.BigDecimal(String.valueOf(value));
                case "BOOLEAN" -> value instanceof Boolean ? value
                        : Boolean.parseBoolean(String.valueOf(value));
                case "DATE" -> value instanceof Date ? value : com.alibaba.fastjson2.util.DateUtils.parseDate(String.valueOf(value));
                case "JSON" -> value instanceof JSONObject || value instanceof JSONArray ? value
                        : JSON.parse(String.valueOf(value));
                default -> value;
            };
        } catch (Exception e) {
            throw new ServiceException("参数 " + name + " 类型不符（期望 " + type + "）：" + e.getMessage());
        }
    }
}
