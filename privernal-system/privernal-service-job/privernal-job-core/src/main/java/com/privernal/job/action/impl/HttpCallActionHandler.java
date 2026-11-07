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
import com.privernal.system.http.HttpOutboundClient;
import com.privernal.system.http.OutboundRequest;
import com.privernal.system.http.OutboundResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

/**
 * HTTP 回调动作（HTTP_CALL /）。
 *
 * <p>传输统一走 HttpOutboundClient 公共出站栈（与 Webhook 出站共用连接配置与
 * 永久失败判定，D1-B 决策落地）；SSRF 基础过滤：仅 http/https、禁解析到
 * 内网/回环地址、出站栈本身不跟随重定向。</p>
 *
 * <p>config: { url, method: GET|POST, body?, contentType? }</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpCallActionHandler implements ScheduleActionHandler {

    private final HttpOutboundClient outboundClient;

    @Override
    public String actionType() {
        return "HTTP_CALL";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        Map<String, Object> config = context.getConfig();
        String url = config.get("url") == null ? null : String.valueOf(config.get("url"));
        if (url == null || url.isBlank()) {
            return ActionResult.fail("url 为空");
        }
        String violation = checkUrl(url);
        if (violation != null) {
            return ActionResult.fail("URL 被安全策略拒绝: " + violation);
        }
        OutboundRequest req = new OutboundRequest();
        req.setUrl(url);
        String method = config.get("method") == null ? "GET" : String.valueOf(config.get("method")).toUpperCase();
        req.setMethod(method);
        if ("POST".equals(method)) {
            req.setBody(config.get("body") == null ? "" : String.valueOf(config.get("body")));
            req.getHeaders().put("Content-Type",
                    config.get("contentType") == null ? "application/json" : String.valueOf(config.get("contentType")));
        }
        OutboundResponse resp = outboundClient.send(req);
        String brief = "HTTP " + resp.getStatusCode() + " body~"
                + Math.min(resp.getBody() == null ? 0 : resp.getBody().length(), 200);
        if (resp.isSuccess()) {
            return ActionResult.ok(brief);
        }
        return ActionResult.fail(resp.isExecuted() ? brief : resp.getError());
    }

    /** SSRF 基础过滤：协议白名单 + 目标地址不得为内网/回环（§11） */
    private String checkUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return "非 http/https 协议";
            }
            String host = uri.getHost();
            if (host == null) {
                return "host 缺失";
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                        || address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
                    return "目标为内网/回环地址 " + address.getHostAddress();
                }
            }
            return null;
        } catch (Exception e) {
            return "URL 解析失败: " + e.getMessage();
        }
    }
}
