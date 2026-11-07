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

package com.privernal.dynamic.advanced.engine;

import com.privernal.dynamic.advanced.mapper.DynamicLinkageQueryMapper;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高级设置全局 kill switch（§8.3，D7 决策：与导入导出运行时同批交付）。
 * <p>
 * 开关存 `system_default_config`（config_key → config_value），不重启即可关停：
 * `dynamic.advanced.io.enabled`（导入导出）/ `dynamic.advanced.share.enabled`（外链）/
 * `dynamic.advanced.linkage.enabled`（联动）。缺行或值非 'N' 一律视为启用（缺省开放，
 * 事故时手工 INSERT/UPDATE 一行 'N' 即关停）。30s 内存缓存，读路径零额外压力。
 */
@Slf4j
@Component
public class AdvancedKillSwitch {

    public static final String KEY_IO = "dynamic.advanced.io.enabled";
    public static final String KEY_SHARE = "dynamic.advanced.share.enabled";
    public static final String KEY_LINKAGE = "dynamic.advanced.linkage.enabled";

    private static final long CACHE_MS = 30_000L;

    @Autowired
    private DynamicLinkageQueryMapper linkageQueryMapper;

    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
    private final AtomicLong loadedAt = new AtomicLong(0);

    /** 导入导出是否可用（模板/导入/导出共用一闸） */
    public boolean ioEnabled() {
        return enabled(KEY_IO);
    }

    /** 外链是否可用（免登 5 端点 + 管理端配置写操作共用一闸） */
    public boolean shareEnabled() {
        return enabled(KEY_SHARE);
    }

    /** 联动是否可用（事件分发 + TIMER/DATE_FIELD 触发共用一闸） */
    public boolean linkageEnabled() {
        return enabled(KEY_LINKAGE);
    }

    private boolean enabled(String key) {
        refreshIfNeeded();
        return cache.getOrDefault(key, Boolean.TRUE);
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
            // 先重置为启用再覆盖：配置行被删除后要能自动恢复（不能残留上次的关停态）
            cache.put(KEY_IO, Boolean.TRUE);
            cache.put(KEY_SHARE, Boolean.TRUE);
            cache.put(KEY_LINKAGE, Boolean.TRUE);
            List<Map<String, Object>> rows = linkageQueryMapper.selectRows(
                    "SELECT config_key AS configKey, config_value AS configValue FROM system_default_config "
                            + "WHERE config_key IN ('" + KEY_IO + "','" + KEY_SHARE + "','" + KEY_LINKAGE + "')",
                    Map.of());
            for (Map<String, Object> row : rows) {
                String key = String.valueOf(row.get("configKey"));
                Object value = row.get("configValue");
                // config_value 是 json 列：可能回读为 "N"（含引号）或 N
                String text = value == null ? "" : String.valueOf(value).replace("\"", "").trim();
                cache.put(key, !"N".equalsIgnoreCase(text));
            }
        } catch (Exception e) {
            // 配置表不可读时按全启用兜底（不能因 kill switch 自身故障停摆业务）
            log.warn("高级设置 kill switch 读取失败，按启用处理: {}", e.getMessage());
        }
    }
}
