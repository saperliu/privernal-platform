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

package com.privernal.common.core.utils.html;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Objects;

/**
 * http请求工具类
 * @author saperliu
 * @date 2025/07/28
 */
@Slf4j
public class HttpClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /**
     * OkHttp 客户端持有连接池与 Dispatcher 线程池，官方要求全局复用；按请求 new 会持续泄漏这些资源
     */
    private static final OkHttpClient CLIENT = new OkHttpClient();

    public static String get(String url, Map<String, Object> params) throws Exception {

        return getWithHeaders(url, params, null);
    }

    public static String getWithHeaders(String url, Map<String, Object> params, Map<String, String> headers) throws Exception {

        Assert.hasLength(url, "url不能为空");

        // 构建带参数的 URL
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url), "url格式不合法").newBuilder();

        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), Objects.toString(entry.getValue(), null));
            }
        }

        HttpUrl httpUrl = urlBuilder.build();

        log.info("HttpClient请求url:{}", stripQuery(httpUrl.toString()));

        Request.Builder newBuilder = new Request.Builder();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                newBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        } else {
            newBuilder.addHeader("Content-Type", "application/json");
        }

        Request request = newBuilder
                .get()
                .url(httpUrl)
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            ResponseBody body = response.body();
            Assert.notNull(body, "返回数据为NULL");
            return body.string();
        }
    }

    public static String post(String url, Object params) throws Exception {
        return postWithHeaders(url, params, null);
    }

    public static String postWithHeaders(String url, Object params, Map<String, String> headers) throws Exception {

        Assert.hasLength(url, "url不能为空");

        String jsonBody = "";
        if (params != null) {
            jsonBody = JSON.toJSONString(params);
        }

        RequestBody body = RequestBody.create(jsonBody, JSON_TYPE);

        Request.Builder newBuilder = new Request.Builder();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                newBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        } else {
            newBuilder.addHeader("Content-Type", "application/json");
        }

        Request request = newBuilder
                .post(body)
                .url(url)
                .build();

        log.info("HttpClient请求url:{}", stripQuery(url));

        try (Response response = CLIENT.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            Assert.notNull(responseBody, "返回数据为NULL");
            return responseBody.string();
        }
    }

    /**
     * 第三方接口常把 access_token 之类凭据放在查询串或 URL 里，日志只保留协议+主机+路径
     */
    private static String stripQuery(String url) {
        int mark = url.indexOf('?');
        return mark < 0 ? url : url.substring(0, mark);
    }
}
