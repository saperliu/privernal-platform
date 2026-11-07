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

package com.privernal.dynamic.datav.service.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HTTP 数据源的地址闸门测试
 * <p>
 * 测的是「连接前最后一道校验」：白名单只能管主机名，SSRF 的成立与否取决于真正被拨的那个地址。
 * 用 IP 字面量做用例，{@code getAllByName} 不会走 DNS，因此不依赖网络环境。
 *
 * @author saperliu
 */
@DisplayName("HTTP 数据源地址闸门测试")
class HttpSourceConnectorSsrfTest {

    private final HttpSourceConnector connector = new HttpSourceConnector();

    @Test
    @DisplayName("内网 IP 字面量被拒，且原因可读")
    void rejectsInternalLiteral() {
        assertThatThrownBy(() -> connector.vetAddresses("127.0.0.1"))
                .isInstanceOf(UnknownHostException.class);
        assertThat(connector.blockedReason()).contains("内网/保留地址").contains("127.0.0.1");
    }

    @Test
    @DisplayName("云元数据与私网段同样被拒")
    void rejectsMetadataAndPrivateRanges() {
        assertThatThrownBy(() -> connector.vetAddresses("169.254.169.254"))
                .isInstanceOf(UnknownHostException.class);
        assertThatThrownBy(() -> connector.vetAddresses("10.0.0.8"))
                .isInstanceOf(UnknownHostException.class);
        assertThatThrownBy(() -> connector.vetAddresses("192.168.10.10"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    @DisplayName("公网地址原样放行给连接层")
    void allowsPublicAddress() throws Exception {
        java.util.List<InetAddress> vetted = connector.vetAddresses("8.8.8.8");
        assertThat(vetted).hasSize(1);
        assertThat(vetted.get(0).getHostAddress()).isEqualTo("8.8.8.8");
        assertThat(connector.blockedReason()).isNull();
    }

    @Test
    @DisplayName("localhost 这种全内网解析结果整体失败，而不是悄悄换个地址连")
    void rejectsWhenEveryRecordBlocked() {
        assertThatThrownBy(() -> connector.vetAddresses("localhost"))
                .isInstanceOf(UnknownHostException.class);
    }

    /**
     * 这一组测的是**真实入口**，不是 vetAddresses 本身。
     * 上一轮把否决从 guardHost 移进 Dns 回调时，IP 字面量这条最直白的路径反而没人管了
     * （OkHttp 对 IP 不调 Dns），而单测因为只断言 vetAddresses 的行为照样全绿——
     * 是端到端打真实接口才看见 127.0.0.1 变成了「Failed to connect」而不是「禁止访问」。
     */
    @Test
    @DisplayName("配置里直接写内网 IP 时，必须在建连前就被拒")
    void vetoesLiteralAddressesBeforeConnecting() {
        for (String url : new String[]{
                "http://127.0.0.1:3399/x",
                "http://169.254.169.254/latest/meta-data/",
                "http://10.42.7.13:8080/x",
                "http://192.168.1.1/x",
                "http://[::1]:9200/x",
                "http://0.0.0.0/x"}) {
            com.alibaba.fastjson2.JSONObject config = new com.alibaba.fastjson2.JSONObject();
            config.put("url", url);
            config.put("method", "GET");
            assertThatThrownBy(() -> connector.sampleColumns(config))
                    .as("url=%s", url)
                    .isInstanceOf(com.privernal.common.core.exception.ServiceException.class)
                    .hasMessageContaining("禁止访问内网/保留地址");
        }
    }

    @Test
    @DisplayName("字面量判定绝不做 DNS，公网地址照常放行")
    void literalCheckNeverResolvesHostnames() throws Exception {
        assertThat(HttpSourceConnector.literalAddress("example.com")).isNull();
        assertThat(HttpSourceConnector.literalAddress("localhost")).isNull();
        assertThat(HttpSourceConnector.literalAddress("")).isNull();
        // 畸形地址不在这里当内网处理，交给连接阶段报解析失败
        assertThat(HttpSourceConnector.literalAddress("999.1.1.1")).isNull();
        // 公网字面量照常放行
        assertThat(HttpSourceConnector.literalAddress("8.8.8.8")).isNotNull();
        assertThat(HttpSourceConnector.isBlocked(
                java.net.InetAddress.getByName("8.8.8.8"))).isFalse();
        // 主机名形式仍然由 Dns 回调兜住
        assertThatThrownBy(() -> connector.vetAddresses("127.0.0.1"))
                .isInstanceOf(java.net.UnknownHostException.class);
    }

    @Test
    @DisplayName("IPv4 映射的 IPv6 写法不能绕过判定")
    void vetoesIpv4MappedIpv6() throws Exception {
        java.net.InetAddress mapped = java.net.InetAddress.getByName("::ffff:127.0.0.1");
        assertThat(HttpSourceConnector.isBlocked(mapped))
                .as("JDK 会把它还原成 Inet4Address，环回判定应仍然成立")
                .isTrue();
        com.alibaba.fastjson2.JSONObject config = new com.alibaba.fastjson2.JSONObject();
        config.put("url", "http://[::ffff:127.0.0.1]:80/x");
        assertThatThrownBy(() -> connector.sampleColumns(config))
                .hasMessageContaining("禁止访问内网/保留地址");
    }

    @Test
    @DisplayName("健康检查按类别回报，而不是把异常丢给调用方")
    void healthReportsCategory() {
        com.alibaba.fastjson2.JSONObject internal = new com.alibaba.fastjson2.JSONObject();
        internal.put("url", "http://169.254.169.254/latest/meta-data/");
        com.alibaba.fastjson2.JSONObject result = connector.health(internal);
        assertThat(result.getBoolean("ok")).isFalse();
        assertThat(result.getString("category")).isEqualTo(DatavSourceException.CONFIG);
        assertThat(result.getString("message")).contains("禁止访问内网/保留地址");

        com.alibaba.fastjson2.JSONObject noUrl = new com.alibaba.fastjson2.JSONObject();
        assertThat(connector.health(noUrl).getString("category")).isEqualTo(DatavSourceException.CONFIG);

        com.alibaba.fastjson2.JSONObject badScheme = new com.alibaba.fastjson2.JSONObject();
        badScheme.put("url", "file:///etc/passwd");
        assertThat(connector.health(badScheme).getString("category")).isEqualTo(DatavSourceException.CONFIG);

        // 203.0.113.5 是 RFC 5737 的文档专用网段：它是**公网**地址所以不会被 SSRF 闸门拦下，
        // 又一定不可路由，所以只会以网络失败收场。环回地址在这儿是错的——那会被闸门拦下、判成 config。
        com.alibaba.fastjson2.JSONObject unreachable = new com.alibaba.fastjson2.JSONObject();
        unreachable.put("url", "http://203.0.113.5:80/x");
        unreachable.put("timeoutMs", 1500);
        assertThat(connector.health(unreachable).getString("category")).isEqualTo(DatavSourceException.NETWORK);
    }

    /**
     * 响应体上限必须能离线回归：公网没有稳定的大于 2MB 的 JSON 端点可打，
     * 而「上限到底生效没有」是安全承诺，不能只靠读代码确认。
     * 早期实现是先把整个响应读成字符串再判长度——那等于超大响应已经先把堆打满。
     */
    @Test
    @DisplayName("响应体按字节流限长，声明超限与流内超限都能拦下")
    void capsResponseBodyWhileStreaming() throws Exception {
        byte[] big = new byte[3 * 1024 * 1024];
        java.util.Arrays.fill(big, (byte) 'a');

        // 带 Content-Length 的：一个字节都不该读
        assertThatThrownBy(() -> HttpSourceConnector.readCapped(big.length,
                new java.io.ByteArrayInputStream(big)))
                .isInstanceOf(DatavSourceException.class)
                .extracting(e -> ((DatavSourceException) e).getCategory())
                .isEqualTo(DatavSourceException.LIMIT);

        // 分块传输（没有 Content-Length）：靠边读边累计拦下
        assertThatThrownBy(() -> HttpSourceConnector.readCapped(-1L,
                new java.io.ByteArrayInputStream(big)))
                .isInstanceOf(DatavSourceException.class)
                .extracting(e -> ((DatavSourceException) e).getCategory())
                .isEqualTo(DatavSourceException.LIMIT);

        // 上限之内正常返回：别把限制做得比真实数据还小
        byte[] small = new byte[64 * 1024];
        java.util.Arrays.fill(small, (byte) 'x');
        String text = HttpSourceConnector.readCapped(-1L, new java.io.ByteArrayInputStream(small));
        assertThat(text).hasSize(small.length);
    }
}
