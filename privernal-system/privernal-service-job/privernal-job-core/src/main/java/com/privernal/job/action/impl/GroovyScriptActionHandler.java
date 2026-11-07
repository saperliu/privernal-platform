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

package com.privernal.job.action.impl;

import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import com.privernal.job.security.GroovySandboxValidator;
import com.privernal.dynamic.dbs.groovy.GroovyScriptService;
import com.privernal.dynamic.script.engine.ScriptAssetExecutor;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Groovy 脚本动作（GROOVY_SCRIPT）。
 *
 * <p>复用 GroovyScriptService 的 ThreadInterrupt 超时中断与编译缓存；P1-1 起脚本走
 * {@code executeSandboxed}：编译期套同一套白名单 AST（与注册校验同源），并按任务的
 * {@code timeout_seconds} 硬接线到 Future（上限 {@value #MAX_SCRIPT_TIMEOUT_SECONDS}s）。
 * 绑定变量沿用 GroovyScriptService 既有约定（daoService / requestData），
 * requestData 取任务 biz_context。</p>
 *
 * <p>config 支持两种形态：
 * ① 裸脚本 {@code { "script": "..." }}（兼容保留，沙箱校验后执行）；
 * ② 资产引用 {@code { "scriptCode": "...", "params": {...} }}——走脚本资产执行器
 * （沙箱 + 参数契约 + 输出上限 + 每组织配额 + 执行日志），优先推荐。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroovyScriptActionHandler implements ScheduleActionHandler {

    /** 脚本硬超时上限（P1-1）：任务可配 timeout_seconds，但不允许超过 300s 占用执行池 */
    static final int MAX_SCRIPT_TIMEOUT_SECONDS = 300;

    private final GroovyScriptService groovyScriptService;
    private final GroovySandboxValidator sandboxValidator;
    private final ScriptAssetExecutor scriptAssetExecutor;

    @Override
    public String actionType() {
        return "GROOVY_SCRIPT";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        Map<String, Object> config = context.getConfig();
        Object scriptCodeObj = config.get("scriptCode");
        if (scriptCodeObj != null && !String.valueOf(scriptCodeObj).isBlank()) {
            return runScriptAsset(task, String.valueOf(scriptCodeObj), config.get("params"));
        }
        Object scriptObj = config.get("script");
        if (scriptObj == null || String.valueOf(scriptObj).isBlank()) {
            return ActionResult.fail("script 为空");
        }
        String script = String.valueOf(scriptObj);
        // 执行前复核（注册后脚本仍可能被改配置替换，§8.1）
        try {
            sandboxValidator.validate(script);
        } catch (IllegalArgumentException e) {
            return ActionResult.fail("脚本未通过安全校验: " + e.getMessage());
        }
        Object requestData = task.getBizContext() == null ? null : JSONUtil.parseObj(task.getBizContext());
        // P1-1：timeout_seconds 真正生效——min(任务超时, 300s)，取不到时由服务侧全局默认兜底
        Integer configured = task.getTimeoutSeconds();
        long scriptTimeout = configured == null || configured <= 0
                ? 0 : Math.min(configured.longValue(), MAX_SCRIPT_TIMEOUT_SECONDS);
        try {
            groovyScriptService.executeSandboxed(script, requestData, scriptTimeout);
            return ActionResult.ok("script executed, length=" + script.length());
        } catch (Exception e) {
            log.error("[schedule] groovy script failed taskId={}", task.getId(), e);
            return ActionResult.fail(e.getMessage());
        }
    }

    /** 资产引用路径：params 支持任务 biz_context 里的键名引用（{"参数名": "$biz.键名"} 或直接常量） */
    private ActionResult runScriptAsset(ScheduleTaskSnapshot task, String scriptCode, Object paramsObj) {
        Map<String, Object> params = Map.of();
        if (paramsObj instanceof Map<?, ?> rawMap) {
            params = resolveBizRefs(rawMap, task.getBizContext());
        }
        try {
            Object result = scriptAssetExecutor.runByCode(scriptCode, params,
                    com.privernal.dynamic.script.domain.DynamicScriptExecLog.POINT_SCHEDULE,
                    null, null, task.getId());
            String summary = result == null ? "no result" : String.valueOf(result);
            return ActionResult.ok("scriptAsset " + scriptCode + " → "
                    + (summary.length() > 200 ? summary.substring(0, 200) : summary));
        } catch (Exception e) {
            log.error("[schedule] script asset failed taskId={} code={}", task.getId(), scriptCode, e);
            return ActionResult.fail(e.getMessage());
        }
    }

    /** params 值以 "$biz." 开头时从任务 biz_context 取值，其余按常量 */
    private Map<String, Object> resolveBizRefs(Map<?, ?> rawMap, String bizContext) {
        java.util.Map<String, Object> resolved = new java.util.HashMap<>();
        cn.hutool.json.JSONObject biz = null;
        if (bizContext != null && !bizContext.isBlank()) {
            try {
                biz = JSONUtil.parseObj(bizContext);
            } catch (Exception ignored) {
                // biz_context 非法时按无上下文处理，$biz. 引用落 null
            }
        }
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof String text && text.startsWith("$biz.")) {
                String bizKey = text.substring(5);
                resolved.put(key, biz == null ? null : biz.get(bizKey));
            } else {
                resolved.put(key, value);
            }
        }
        return resolved;
    }
}
