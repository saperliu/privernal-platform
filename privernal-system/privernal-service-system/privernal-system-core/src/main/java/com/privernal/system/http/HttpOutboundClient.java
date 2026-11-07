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

package com.privernal.system.http;

import com.privernal.common.core.utils.net.OutboundHttpGuard;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 公共 HTTP 出站栈（HttpOutboundClient）——平台内所有时间驱动/事件驱动的
 * 出站调用共用同一份连接配置、双 SSL 客户端选择与"永久失败"判定，不自建第二套 HttpClient。
 *
 * <p>当前接入方：WebhookDispatcher（事件驱动 Webhook 推送）、定时任务 HTTP_CALL 动作与
 * OPENAPI callback_url（时间驱动回调，privernal-job-core）。重试/退避语义因场景而异，留给
 * 调用方编排；本类只做单次传输并给出 success/permanentFailure 判定
 * （4xx 除 408/429 视为对方明确拒绝，重试无意义）。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
public class HttpOutboundClient {

    private HttpClient verifyingClient;

    private HttpClient trustingClient;

    @PostConstruct
    public void init() {
        verifyingClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        trustingClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .sslContext(trustAllSslContext())
                // sslVerify=0（自签证书场景）仍保留端点主机名校验：
                // 信任任意签发者 ≠ 信任任意证书，关掉主机名校验等于向同链路攻击者敞开 MITM
                .build();
        log.info("[outbound] HttpOutboundClient ready (verifying/trusting dual clients)");
    }

    /** 单次出站传输：不发重试（退避编排归调用方），异常转 error 不抛出 */
    public OutboundResponse send(OutboundRequest req) {
        OutboundResponse result = new OutboundResponse();
        long start = System.currentTimeMillis();
        try {
            // SSRF 总闸：所有经本栈出站的请求（Webhook 投递 / OPENAPI 回调 / HTTP_CALL）
            // 一律过协议白名单 + IP 字面量否决 + DNS 逐地址否决，目标地址是调用方可控输入
            OutboundHttpGuard.check(req.getUrl(), "出站请求");
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(req.getUrl()))
                    .timeout(Duration.ofMillis(req.getTimeoutMs()))
                    .header("Content-Type", "application/json;charset=UTF-8");
            if (req.getHeaders() != null) {
                req.getHeaders().forEach(builder::header);
            }
            String method = req.getMethod() == null ? "POST" : req.getMethod().toUpperCase();
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(
                        req.getBody() == null ? "" : req.getBody(), java.nio.charset.StandardCharsets.UTF_8));
            }
            HttpClient client = req.isTrustAllSsl() ? trustingClient : verifyingClient;
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            result.setExecuted(true);
            result.setStatusCode(response.statusCode());
            result.setBody(response.body());
            result.setSuccess(response.statusCode() >= 200 && response.statusCode() < 300);
            result.setPermanentFailure(!result.isSuccess()
                    && response.statusCode() >= 400 && response.statusCode() < 500
                    && response.statusCode() != 408 && response.statusCode() != 429);
        } catch (Exception e) {
            result.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        result.setCostMs(System.currentTimeMillis() - start);
        return result;
    }

    private static SSLContext trustAllSslContext() {
        try {
            TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, new SecureRandom());
            return context;
        } catch (Exception e) {
            throw new IllegalStateException("初始化免校验 SSLContext 失败", e);
        }
    }
}
