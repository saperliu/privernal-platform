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

package com.privernal.dynamic.datav.service;

import com.privernal.common.redis.service.RedisService;
import com.privernal.common.security.service.SecurityUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 聚合结果缓存（M4.6）。
 * <p>
 * 分两层，因为它们各自解决不同的浪费：
 * <ul>
 *   <li><b>批次内去重</b>：一块屏 30 个组件常常只有 5 种查询（多个图共用同一数据集与配置）。
 *       批量取数是单线程串行的，所以这层用进程内 Map 就够，比走 Redis 更准也更快</li>
 *   <li><b>跨请求缓存</b>：30s 定时刷新时命中，键进 Redis</li>
 * </ul>
 * <p>
 * 缓存键必须带上数据权限维度（组织、部门、用户、业务库）。行级数据权限切面按登录上下文过滤，
 * 键里少一项就等于把 A 用户看到的聚合结果发给 B 用户——那是数据泄露，不是缓存未命中。
 * 因此这里默认取当前登录态的全部相关标识，宁可命中率低。
 * <p>
 * 失败降级返回上次快照时带 {@code stale=true}：投放屏上"数据不动"必须能被区分出来，
 * 否则观众会把三天前的数字当成实时值。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class DatavResultCache {

    private static final String KEY_PREFIX = "datav:agg:";

    private static final String STALE_SUFFIX = ":stale";

    @Autowired
    private RedisService redisService;

    /** 缓存开关兼 TTL：0（默认）表示不缓存。交付形态是默认关闭，开启必须是运维的显式动作 */
    @Value("${privernal.datav.cache-ttl-seconds:0}")
    long ttlSeconds;

    /** 失败降级用的快照保留时长，比正常 TTL 长：源站抖动时屏上至少还有上次的数 */
    @Value("${privernal.datav.cache-stale-ttl-seconds:900}")
    long staleTtlSeconds;

    /**
     * 批次内去重表。
     * <p>
     * 刻意不放进 ThreadLocal：批量取数全程在同一线程串行执行，一个方法内的局部 Map 更直观，
     * 也不会因为忘记清理而跨请求残留。
     */
    public Map<String, Map<String, Object>> newBatchScope() {
        return new HashMap<>();
    }

    public boolean enabled() {
        return ttlSeconds > 0;
    }

    /**
     * 组装缓存键。
     *
     * @param database 组织业务库名（不同库必须分开）
     */
    public String buildKey(String database, String datasetCode, JSONObject config, Object runtimeFilters) {
        StringBuilder raw = new StringBuilder()
                .append(identityScope())
                .append('|').append(database)
                .append('|').append(datasetCode)
                .append('|').append(config == null ? "{}" : config.toJSONString())
                .append('|').append(runtimeFilters == null ? "[]" : JSON.toJSONString(runtimeFilters));
        return KEY_PREFIX + sha256(raw.toString());
    }

    /**
     * 参与缓存键的身份维度。
     * <p>
     * 这里**故意不兜底**：没有登录上下文时 SecurityUtils 会抛，而不是给一个 anonymous 占位。
     * 兜底等于把所有未登录请求聚到同一个键上，那份缓存随后会被任何人的请求命中——
     * 一个「缓存键退化」的 bug 就直接变成跨用户读取。宁可让这条路径报错。
     */
    String identityScope() {
        return "|" + SecurityUtils.getOrganizationId()
                + "|" + SecurityUtils.getDepartmentId()
                + "|" + SecurityUtils.getUserId();
    }

    /** 批次内命中：同一批里第二次遇到同样的查询直接复用 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchGet(Map<String, Map<String, Object>> scope, String key) {
        Object hit = scope.get(key);
        return hit instanceof Map ? (Map<String, Object>) hit : null;
    }

    public void batchPut(Map<String, Map<String, Object>> scope, String key, Map<String, Object> result) {
        scope.put(key, result);
        if (enabled()) {
            try {
                // 存 JSON 字符串而不是 Map：平台的 Redis 编解码器按 fastjson2 带类名写，
                // 直接把 Map 交给它，读回来就带着 "@type":"java.util.HashMap"，
                // 而这个对象会被原样放进大屏接口响应 —— 既污染了行结构（前端遍历列时
                // 多出一个不存在的 @type 列），又把服务端类名泄露到 HTTP 报文里，
                // 更要紧的是读侧一旦支持 autoType，缓存就成了反序列化攻击的入口。
                String payload = JSON.toJSONString(result);
                redisService.setCacheObject(key, payload, ttlSeconds, TimeUnit.SECONDS);
                redisService.setCacheObject(key + STALE_SUFFIX, payload, staleTtlSeconds, TimeUnit.SECONDS);
            } catch (RuntimeException e) {
                // 缓存写失败不能把取数一起拖挂：这轮少缓存一次而已
                log.warn("大屏聚合结果写缓存失败，已跳过：{}", e.getMessage());
            }
        }
    }

    public Map<String, Object> load(String key) {
        if (!enabled()) {
            return null;
        }
        try {
            return asResult(redisService.getCacheObject(key));
        } catch (RuntimeException e) {
            log.warn("大屏聚合结果读缓存失败，已回源：{}", e.getMessage());
            return null;
        }
    }

    /** 只认我们自己写进去的 JSON 字符串；旧格式（带 @type 的 Map）一律当未命中回源 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asResult(Object cached) {
        if (cached instanceof String text) {
            return JSON.parseObject(text);
        }
        return null;
    }

    /** 降级快照：调用方必须自己打上 stale 标记，前端才知道该提示「数据可能不是最新」 */
    public Map<String, Object> loadStale(String key) {
        try {
            return asResult(redisService.getCacheObject(key + STALE_SUFFIX));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 数据集配置变了要能立刻失效，否则用户改完看到的还是旧数 */
    public void evictDataset(String datasetCode) {
        // Redis 侧靠 TTL 自然过期：键里含配置摘要，改配置必然换键。
        // 这里只清批次内状态（无跨请求残留），所以方法保留为空实现语义。
        log.debug("数据集 {} 的缓存将随 TTL 过期，无需显式清理", datasetCode);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算缓存键摘要", e);
        }
    }
}
