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

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 聚合结果缓存测试（M4.6）
 * <p>
 * 不测 Redis 读写本身（那是 RedisService 的事），测的是这个类自己的三个决定：
 * 默认关闭、批次内确实去重、键能区分开不同配置与不同数据权限范围。
 *
 * @author saperliu
 */
@DisplayName("大屏聚合结果缓存测试")
class DatavResultCacheTest {

    /** 固定身份维度的子类：生产实现要读登录上下文，离线用例里没有 */
    private static final class TestCache extends DatavResultCache {
        private final String who;

        TestCache(long ttl, String who) {
            this.ttlSeconds = ttl;
            this.staleTtlSeconds = Math.max(ttl, 900L);
            this.who = who;
        }

        @Override
        String identityScope() {
            return "|" + who;
        }
    }

    private DatavResultCache cacheWithTtl(long ttl) {
        return new TestCache(ttl, "org-1|dept-1|user-1");
    }

    @Test
    @DisplayName("默认不缓存：TTL 为 0 时既读不到也不写 Redis")
    void disabledByDefault() {
        DatavResultCache cache = cacheWithTtl(0);
        assertThat(cache.enabled()).isFalse();
        // 关闭时 load 必须直接返回 null，不能去碰 Redis（RedisService 在此为 null，碰了就 NPE）
        assertThat(cache.load("datav:agg:any")).isNull();
        assertThat(cache.loadStale("datav:agg:any")).isNull();

        Map<String, Map<String, Object>> scope = cache.newBatchScope();
        cache.batchPut(scope, "k", Map.of("rows", JSONArray.of()));
        // 批次内去重与 Redis 无关，关闭缓存时仍然生效（同一次 batch 里的重复查询照样应该合并）
        assertThat(cache.batchGet(scope, "k")).isNotNull();
    }

    @Test
    @DisplayName("缓存键区分数据集、配置与运行时参数，相同组合稳定同键")
    void keySeparatesEveryThingThatChangesTheAnswer() {
        DatavResultCache cache = cacheWithTtl(0);
        JSONObject config = new JSONObject();
        config.put("reportType", "bar");
        config.put("dimensions", JSONArray.of(new JSONObject(Map.of("field", "city"))));

        String base = cache.buildKey("db_a", "ds_1", config, null);
        assertThat(base).isEqualTo(cache.buildKey("db_a", "ds_1", config, null));
        // 换库/换数据集/换配置/换运行时参数，任一项都必须换键：
        // 少一项就会把 A 的聚合结果发给 B，那是数据泄露而不是缓存未命中
        assertThat(cache.buildKey("db_b", "ds_1", config, null)).isNotEqualTo(base);
        assertThat(cache.buildKey("db_a", "ds_2", config, null)).isNotEqualTo(base);

        JSONObject otherConfig = JSONObject.parseObject(config.toJSONString());
        otherConfig.put("limit", 10);
        assertThat(cache.buildKey("db_a", "ds_1", otherConfig, null)).isNotEqualTo(base);

        JSONArray filters = JSONArray.of(new JSONObject(Map.of("field", "city", "op", "eq", "value", "北京")));
        assertThat(cache.buildKey("db_a", "ds_1", config, filters)).isNotEqualTo(base);

        // 换身份必须换键：这一条防的是「A 的聚合结果被发给 B」
        DatavResultCache other = new TestCache(0, "org-1|dept-1|user-2");
        assertThat(other.buildKey("db_a", "ds_1", config, null)).isNotEqualTo(base);
        DatavResultCache otherOrg = new TestCache(0, "org-2|dept-1|user-1");
        assertThat(otherOrg.buildKey("db_a", "ds_1", config, null)).isNotEqualTo(base);
    }

    @Test
    @DisplayName("键里不含明文身份，避免 Redis 里能直接读出组织与用户")
    void keyDoesNotLeakIdentityInPlainText() {
        DatavResultCache cache = cacheWithTtl(0);
        String key = cache.buildKey("campus_db", "ds_1", new JSONObject(), null);
        assertThat(key).startsWith("datav:agg:");
        // 键体是摘要：身份与库名参与计算但不落地成可读文本
        assertThat(key).doesNotContain("campus_db").doesNotContain("ds_1");
    }
}
