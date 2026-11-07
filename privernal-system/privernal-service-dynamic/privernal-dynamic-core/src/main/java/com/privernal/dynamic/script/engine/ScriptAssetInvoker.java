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
import com.privernal.dynamic.script.domain.DynamicScriptAsset;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 脚本资产的互调门面（绑定变量 {@code scriptAssets}，plan §3-C1）：
 * {@code scriptAssets.invoke('脚本编码', [参数名: 值])}。
 * <p>
 * 互调深度 ≤3、环路检测（同一次顶层运行内不允许重复进入同一资产），更多层次应拆外部服务。
 * 只允许调用当前组织内 PUBLISHED 资产（状态校验在执行器侧强制）。
 */
@Slf4j
public class ScriptAssetInvoker {

    private static final int MAX_DEPTH = 3;

    private static final ThreadLocal<Deque<String>> CALL_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private final ScriptAssetExecutor executor;

    ScriptAssetInvoker(ScriptAssetExecutor executor) {
        this.executor = executor;
    }

    /**
     * 按编码调用另一脚本资产（当前发布版）。
     *
     * @param scriptCode 目标资产编码（组织内）
     * @param params     参数 Map（Groovy 侧传 map 字面量）
     */
    public Object invoke(String scriptCode, Map<String, Object> params) {
        if (StringUtils.isEmpty(scriptCode)) {
            throw new ServiceException("scriptAssets.invoke 缺少 scriptCode");
        }
        Deque<String> stack = CALL_STACK.get();
        if (stack.size() >= MAX_DEPTH) {
            throw new ServiceException("脚本资产互调深度超过上限（" + MAX_DEPTH + "），请拆分资产或改用外部服务");
        }
        if (stack.contains(scriptCode)) {
            throw new ServiceException("脚本资产互调出现环路：" + stack + " → " + scriptCode);
        }
        stack.push(scriptCode);
        try {
            log.debug("脚本资产互调 depth={} code={}", stack.size(), scriptCode);
            return executor.runByCode(scriptCode, params,
                    com.privernal.dynamic.script.domain.DynamicScriptExecLog.POINT_LINKAGE_SCRIPT,
                    null, null, null);
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                CALL_STACK.remove();
            }
        }
    }

    /** 供测试/管理端在运行结束后强制清栈（线程复用兜底） */
    public static void clearStack() {
        CALL_STACK.remove();
    }

    /** 资产互调不计入调用方传入的 formId/tableCode（审计里以 bindPoint=LINKAGE_SCRIPT 区分） */
    public boolean isPublished(DynamicScriptAsset asset) {
        return asset != null && DynamicScriptAsset.STATUS_PUBLISHED.equals(asset.getStatus());
    }

    /** 防御性保留：互调过程中累积的待运行集合（预留环路告警扩展） */
    public Set<String> currentStack() {
        return new HashSet<>(CALL_STACK.get());
    }
}
