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

package com.privernal.openapi.support;

import com.privernal.system.domain.SystemOpenapiCredential;

/**
 * 当前 OpenAPI 请求的调用上下文。
 * <p>
 * 刻意不建 sa-token 登录态：StpUtil 的 is-share 配置会让机器调用覆盖或注销真人会话。
 * 组织归属由验签过滤器写入 OrganizationContextHolder，凭证里的用户只作数据归属用。
 */
public class OpenApiContext {

    private static final ThreadLocal<Call> HOLDER = new ThreadLocal<>();

    private OpenApiContext() {
    }

    public static void set(Call call) {
        HOLDER.set(call);
    }

    public static Call get() {
        return HOLDER.get();
    }

    public static boolean active() {
        return HOLDER.get() != null;
    }

    /**
     * 业务侧失败标记：过滤器在 finally 里据此把流水写成 FAILED，
     * 这样统计不必去缓存响应体
     */
    public static void markFailed(String message) {
        Call call = HOLDER.get();
        if (call != null) {
            call.failMsg = message;
        }
    }

    public static SystemOpenapiCredential credential() {
        Call call = HOLDER.get();
        return call == null ? null : call.credential;
    }

    /**
     * 业务侧写完回填数据ID，流水里才知道动的是哪条数据（路径里没有 id 的那几个端点）
     */
    public static void markBizId(String bizId) {
        Call call = HOLDER.get();
        if (call != null) {
            call.bizId = bizId;
        }
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 一次调用的可变状态
     */
    public static class Call {
        public String requestId;
        public long startMs;
        public String accessKey;
        public String endpoint;
        public String httpMethod;
        public String action;
        public String tableCode;
        public String bizId;
        public String nonce;
        public String clientIp;
        public SystemOpenapiCredential credential;
        public String result;
        public String errorCode;
        public String denyMsg;
        public String failMsg;
        public Integer statusCode;
        public long costMs;
    }
}
