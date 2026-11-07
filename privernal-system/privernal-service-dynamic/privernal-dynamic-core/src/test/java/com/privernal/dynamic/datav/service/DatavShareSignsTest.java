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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大屏外链签名与 referer 匹配测试
 * <p>
 * 这两个纯函数是外链全部安全语义的载体：签名算错 = 任何链接都打不开或任何链接都能打开；
 * referer 匹配写宽 = 白名单形同虚设。逐条钉住，包括两个最容易被「顺手写对」的绕过形态。
 *
 * @author saperliu
 */
@DisplayName("大屏外链签名与来源匹配测试")
class DatavShareSignsTest {

    private static final String KEY = "unit-master-key";

    @Nested
    @DisplayName("签名")
    class Sign {

        @Test
        @DisplayName("同一密钥同一编码结果稳定；换编码或换密钥都变")
        void deterministicButSensitive() {
            String a1 = DatavShareSigns.signOf(KEY, "dvshare_abc");
            String a2 = DatavShareSigns.signOf(KEY, "dvshare_abc");
            assertThat(a1).isEqualTo(a2).hasSize(64);
            assertThat(DatavShareSigns.signOf(KEY, "dvshare_abd")).isNotEqualTo(a1);
            assertThat(DatavShareSigns.signOf(KEY + "x", "dvshare_abc")).isNotEqualTo(a1);
        }

        @Test
        @DisplayName("主密钥未配置 fail-closed：返回 null 而不是可预测的空签名")
        void failClosedWithoutKey() {
            assertThat(DatavShareSigns.signOf(null, "dvshare_abc")).isNull();
            assertThat(DatavShareSigns.signOf("", "dvshare_abc")).isNull();
            assertThat(DatavShareSigns.signOf(KEY, null)).isNull();
            assertThat(DatavShareSigns.signOf(KEY, "")).isNull();
        }

        @Test
        @DisplayName("常量时间比较：相等 true；长度不同或内容不同 false")
        void constantEquals() {
            assertThat(DatavShareSigns.constantEquals("abcdef", "abcdef")).isTrue();
            assertThat(DatavShareSigns.constantEquals("abcdef", "abcdeg")).isFalse();
            assertThat(DatavShareSigns.constantEquals("abcdef", "abcdef0")).isFalse();
            assertThat(DatavShareSigns.constantEquals(null, "abcdef")).isFalse();
            assertThat(DatavShareSigns.constantEquals("abcdef", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("referer 白名单")
    class Referer {

        @Test
        @DisplayName("白名单为空不限制；配置后缺 Referer 拒绝")
        void emptyAllowsAndMissingDenies() {
            assertThat(DatavShareSigns.refererAllowed(null, null)).isTrue();
            assertThat(DatavShareSigns.refererAllowed("  ", "https://anywhere.com/x")).isTrue();
            assertThat(DatavShareSigns.refererAllowed("portal.example.com", null)).isFalse();
            assertThat(DatavShareSigns.refererAllowed("portal.example.com", "")).isFalse();
        }

        @Test
        @DisplayName("host 精确匹配与子域名后缀匹配都放行")
        void matchesHostAndSubdomain() {
            assertThat(DatavShareSigns.refererAllowed("portal.example.com", "https://portal.example.com/screen")).isTrue();
            assertThat(DatavShareSigns.refererAllowed("portal.example.com", "https://dept.portal.example.com/")).isTrue();
            assertThat(DatavShareSigns.refererAllowed("portal.example.com,other.org", "https://other.org/embed")).isTrue();
            // 端口与路径不参与匹配
            assertThat(DatavShareSigns.refererAllowed("portal.example.com", "http://portal.example.com:8080/x")).isTrue();
        }

        @Test
        @DisplayName("拼接绕过与无关来源拒绝：evil-example.com 不能借着 example.com 的授权进来")
        void rejectsSpliceAndUnrelated() {
            assertThat(DatavShareSigns.refererAllowed("example.com", "https://evil-example.com/")).isFalse();
            assertThat(DatavShareSigns.refererAllowed("example.com", "https://example.com.evil.io/")).isFalse();
            assertThat(DatavShareSigns.refererAllowed("example.com", "https://other.org/")).isFalse();
            // 白名单带协议前缀时按 host 归一再比
            assertThat(DatavShareSigns.refererAllowed("https://example.com", "https://a.example.com/")).isTrue();
        }

        @Test
        @DisplayName("白名单归一：支持中英文逗号、去协议、去端口、去重、小写；全空返回 null")
        void normalizesWhitelist() {
            assertThat(DatavShareSigns.normalizeWhitelist("https://A.com:8443，b.com, a.com"))
                    .isEqualTo("a.com,b.com");
            assertThat(DatavShareSigns.normalizeWhitelist("  ")).isNull();
            assertThat(DatavShareSigns.normalizeWhitelist(null)).isNull();
            assertThat(DatavShareSigns.normalizeWhitelist("not a host !!!")).isNull();
        }
    }
}
