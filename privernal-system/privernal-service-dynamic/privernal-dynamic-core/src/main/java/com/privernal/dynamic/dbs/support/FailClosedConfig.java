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

package com.privernal.dynamic.dbs.support;

import com.privernal.dynamic.advanced.mapper.DynamicLinkageQueryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 防护类配置开关读取器。
 * <p>
 * 与 {@link com.privernal.dynamic.advanced.engine.AdvancedKillSwitch} 的方向相反：
 * kill switch 管的是"关停功能"，缺行 = 启用（fail-open）；本类管的是防护/放宽类配置，
 * 语义一律 <b>fail-closed</b>——缺行或读取失败时按更严格的一侧兜底，灰度靠显式写入种子行。
 * <p>
 * 承载三个键：
 * <ul>
 *   <li>{@code dynamic.script.sandbox.enforced}：强制沙箱。缺行/读失败 = 强制（true）；
 *       显式写入 'N'/'false'/'0' 才豁免；支持组织级行 {@code …enforced:{orgId}} 覆盖全局行（灰度）</li>
 *   <li>{@code dynamic.share.reveal-enabled}：外链回显。缺行/读失败 = 关闭（false），显式 'Y' 才开启</li>
 *   <li>{@code dynamic.share.public-origin}：外链二维码/巡检点码的公开 origin（不复用 NET_ADDRESS）</li>
 * </ul>
 * 30s 内存缓存（与 kill switch 同口径），读路径零额外压力。
 */
@Slf4j
@Component
public class FailClosedConfig {

    public static final String KEY_SANDBOX_ENFORCED = "dynamic.script.sandbox.enforced";
    public static final String KEY_SHARE_REVEAL = "dynamic.share.reveal-enabled";
    public static final String KEY_SHARE_PUBLIC_ORIGIN = "dynamic.share.public-origin";

    private static final long CACHE_MS = 30_000L;

    @Autowired
    private DynamicLinkageQueryMapper linkageQueryMapper;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final AtomicLong loadedAt = new AtomicLong(0);

    /**
     * 表单后台脚本是否强制沙箱（fail-closed：缺行/读失败 = 强制）。
     * 组织级行（key 以 {@code :{orgId}} 结尾）优先于全局行，供灰度期显式豁免单个组织。
     */
    public boolean sandboxEnforced(String organizationId) {
        refreshIfNeeded();
        if (organizationId != null && !organizationId.isEmpty()) {
            String orgRow = cache.get(KEY_SANDBOX_ENFORCED + ":" + organizationId);
            if (orgRow != null) {
                return !isFalseValue(orgRow);
            }
        }
        String global = cache.get(KEY_SANDBOX_ENFORCED);
        return global == null || !isFalseValue(global);
    }

    /** 外链回显是否开启（fail-closed：缺行/读失败 = 关闭） */
    public boolean shareRevealEnabled() {
        refreshIfNeeded();
        String row = cache.get(KEY_SHARE_REVEAL);
        return row != null && parseTruth(row);
    }

    /** 公开 origin（未配置返回 null，调用方必须显式报错，不得回落其它来源） */
    public String sharePublicOrigin() {
        refreshIfNeeded();
        String row = cache.get(KEY_SHARE_PUBLIC_ORIGIN);
        if (row == null) {
            return null;
        }
        String text = row.replace("\"", "").trim();
        return text.isEmpty() ? null : stripTrailingSlash(text);
    }

    private static boolean isFalseValue(String raw) {
        String text = raw.replace("\"", "").trim();
        return "N".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text) || "0".equals(text);
    }

    private static boolean parseTruth(String raw) {
        String text = raw.replace("\"", "").trim();
        return "Y".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private static String stripTrailingSlash(String origin) {
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        long last = loadedAt.get();
        if (now - last < CACHE_MS) {
            return;
        }
        if (!loadedAt.compareAndSet(last, now)) {
            return;
        }
        try {
            // 沙箱开关支持组织级灰度行，按前缀一起取；其余两个键精确匹配。
            // 读取须绕过数据范围切面：这些是全局平台配置（organization_id 为空），切面会追加
            // `AND organization_id = ?` 导致永远查不到而误入 fail-closed 兜底（实测踩坑）
            Boolean prevIgnoreOrg = com.privernal.common.datascope.aspect.DataScopeHelper.getIgnoreOrganization();
            Boolean prevIgnoreDp = com.privernal.common.datascope.aspect.DataScopeHelper.getIgnoreDataPermission();
            com.privernal.common.datascope.aspect.DataScopeHelper.mergeIgnoreStrategy(true, true);
            List<Map<String, Object>> rows;
            try {
                rows = linkageQueryMapper.selectRows(
                        "SELECT config_key AS configKey, config_value AS configValue FROM system_default_config "
                                + "WHERE config_key = '" + KEY_SHARE_REVEAL + "' "
                                + "OR config_key = '" + KEY_SHARE_PUBLIC_ORIGIN + "' "
                                + "OR config_key = '" + KEY_SANDBOX_ENFORCED + "' "
                                + "OR config_key LIKE '" + KEY_SANDBOX_ENFORCED + ":%'",
                        Map.of());
            } finally {
                com.privernal.common.datascope.aspect.DataScopeHelper.mergeIgnoreStrategy(prevIgnoreOrg, prevIgnoreDp);
            }
            Map<String, String> fresh = new ConcurrentHashMap<>();
            for (Map<String, Object> row : rows) {
                String key = String.valueOf(row.get("configKey"));
                Object value = row.get("configValue");
                if (key != null && value != null) {
                    fresh.put(key, String.valueOf(value));
                }
            }
            cache.clear();
            cache.putAll(fresh);
        } catch (Exception e) {
            // 读失败 = fail-closed：清空缓存让各键按缺省的严格侧兜底（沙箱强制/回显关闭）
            cache.clear();
            log.warn("防护类配置开关读取失败，按 fail-closed 兜底: {}", e.getMessage());
        }
    }
}
