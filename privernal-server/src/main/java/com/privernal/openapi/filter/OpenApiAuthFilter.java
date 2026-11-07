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

package com.privernal.openapi.filter;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.redis.service.RedisService;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.openapi.support.OpenApiContext;
import com.privernal.openapi.support.OpenApiSignature;
import com.privernal.system.domain.SystemOpenapiCallLog;
import com.privernal.system.domain.SystemOpenapiCredential;
import com.privernal.system.service.OpenApiCallLogService;
import com.privernal.system.service.OpenApiCredentialService;
import com.privernal.system.support.MachineSecretCipher;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * /openapi/** 的验签与授权过滤器。
 * <p>
 * 与 sa-token 的鉴权链完全分离：/openapi/** 在 ignore.whites 里放行给本过滤器，
 * 机器调用不产生登录态，只把凭证的组织写进 OrganizationContextHolder。
 * <p>
 * 校验顺序（任一步失败即拒绝并记流水）：
 * 必填头齐全 → 时间戳窗口 → nonce 去重 → 凭证有效 → 限流 → HMAC 常量时间比对 → 范围与表/动作白名单。
 */
@Slf4j
public class OpenApiAuthFilter extends OncePerRequestFilter {

    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000L;
    private static final long NONCE_TTL_SECONDS = 300L;
    private static final String NONCE_KEY_PREFIX = "openapi:nonce:";
    private static final String RATE_KEY_PREFIX = "openapi:rate:";
    private static final List<String> BODY_KEYWORD_ACTIONS = Arrays.asList("list", "add", "edit");

    private final OpenApiCredentialService credentialService;
    private final OpenApiCallLogService callLogService;
    private final RedisService redisService;

    public OpenApiAuthFilter(OpenApiCredentialService credentialService,
                             OpenApiCallLogService callLogService,
                             RedisService redisService) {
        this.credentialService = credentialService;
        this.callLogService = callLogService;
        this.redisService = redisService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/openapi/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        OpenApiContext.Call call = new OpenApiContext.Call();
        call.requestId = UUID.randomUUID().toString().replace("-", "");
        call.startMs = System.currentTimeMillis();
        call.endpoint = request.getRequestURI();
        call.httpMethod = request.getMethod();
        call.clientIp = resolveClientIp(request);
        call.nonce = request.getHeader(OpenApiSignature.HEADER_NONCE);
        call.result = OpenApiCallLogService.RESULT_DENIED;
        OpenApiContext.set(call);

        try {
            resolveAction(call);
            String denial = authenticate(request, call);
            if (denial != null) {
                call.denyMsg = denial;
                writeError(response, call, denial);
                return;
            }
            // 组织隔离照常生效，行级部门范围刻意忽略：机器调用没有真人部门
            OrganizationContextHolder.setOrgAndIgnoreDepartment(call.credential.getOrganizationId(), true);
            chain.doFilter(request, response);
        } catch (Exception e) {
            OpenApiContext.markFailed(e.getMessage());
            throw e;
        } finally {
            finishAndRecord(call, response);
            OrganizationContextHolder.clear();
            OpenApiContext.clear();
        }
    }

    /**
     * @return 拒绝原因，null 表示通过
     */
    private String authenticate(HttpServletRequest request, OpenApiContext.Call call) {
        String accessKey = request.getHeader(OpenApiSignature.HEADER_ACCESS_KEY);
        String timestamp = request.getHeader(OpenApiSignature.HEADER_TIMESTAMP);
        String nonce = request.getHeader(OpenApiSignature.HEADER_NONCE);
        String bodyDigest = request.getHeader(OpenApiSignature.HEADER_BODY_DIGEST);
        String signature = request.getHeader(OpenApiSignature.HEADER_SIGNATURE);
        if (StringUtils.isEmpty(accessKey) || StringUtils.isEmpty(timestamp) || StringUtils.isEmpty(nonce)
                || StringUtils.isEmpty(bodyDigest) || StringUtils.isEmpty(signature)) {
            call.errorCode = "MISSING_HEADER";
            return "缺少签名请求头：X-Access-Key/X-Timestamp/X-Nonce/X-Body-Digest/X-Signature";
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            call.errorCode = "BAD_TIMESTAMP";
            return "X-Timestamp 必须是毫秒时间戳";
        }
        if (Math.abs(System.currentTimeMillis() - ts) > TIMESTAMP_WINDOW_MS) {
            call.errorCode = "EXPIRED_TIMESTAMP";
            return "请求时间戳超出允许窗口";
        }

        SystemOpenapiCredential credential = credentialService.selectByAccessKey(accessKey);
        String unusable = OpenApiCredentialService.checkUsable(credential);
        if (unusable != null) {
            call.errorCode = "INVALID_CREDENTIAL";
            call.accessKey = accessKey;
            return unusable;
        }
        call.credential = credential;
        call.accessKey = accessKey;

        // nonce 必须用 setIfAbsent：先查再写会在并发重放下双双通过
        if (!redisService.setCacheObjectIfAbsent(NONCE_KEY_PREFIX + accessKey + ":" + nonce, "1",
                NONCE_TTL_SECONDS, TimeUnit.SECONDS)) {
            call.errorCode = "REPLAY_NONCE";
            return "nonce 已被使用，疑似重放";
        }

        int limit = credential.getRateLimitPerMinute() == null ? 600 : credential.getRateLimitPerMinute();
        long used = redisService.getAutoResetIncrementNum(RATE_KEY_PREFIX + accessKey, 0L, 60L);
        if (used > limit) {
            call.result = OpenApiCallLogService.RESULT_RATE_LIMITED;
            call.errorCode = "RATE_LIMITED";
            return "超出每分钟调用额度：" + limit;
        }

        String secret = credentialService.revealSecret(credential);
        if (StringUtils.isEmpty(secret)) {
            call.errorCode = "SECRET_UNAVAILABLE";
            return "服务端密钥不可用，请轮换密钥后重试";
        }
        String canonical = OpenApiSignature.buildCanonical(call.httpMethod, request.getRequestURI(),
                timestamp, nonce, bodyDigest);
        String expected = OpenApiSignature.hmacSha256Hex(secret, canonical);
        if (!MachineSecretCipher.constantEquals(expected, signature.toLowerCase())) {
            call.errorCode = "BAD_SIGNATURE";
            return "签名校验失败";
        }

        String scope = requiredScope(call.action);
        if (scope != null && !OpenApiCredentialService.scopeAllows(credential, scope)) {
            call.errorCode = "SCOPE_DENIED";
            return "凭证未开通范围：" + scope;
        }
        // ai:build 交叉强制：ai:build 凭证只准走 /openapi/ai/** 一档通道，
        // 普通凭证不准进入该通道——二档（发布/回滚/编辑）与三档（删除/权限侧）在通道上不存在
        boolean aiBuildCredential = OpenApiCredentialService.scopeAllows(credential,
                OpenApiCredentialService.SCOPE_AI_BUILD);
        boolean aiBuildAction = call.action.startsWith("ai:build:");
        if (aiBuildCredential != aiBuildAction) {
            call.errorCode = "SCOPE_DENIED";
            return aiBuildCredential
                    ? "ai:build 凭证只能访问 /openapi/ai/** 一档通道：" + call.endpoint
                    : "/openapi/ai/** 需要 ai:build 范围的专用凭证";
        }
        // flow:start 的表在请求体里，表级授权由业务侧解析后再判
        if (StringUtils.isNotEmpty(call.tableCode)
                && !OpenApiCredentialService.grantAllows(credential, call.tableCode, grantAction(call.action))) {
            call.errorCode = "TABLE_DENIED";
            return "该表未授权此动作：" + call.tableCode + " " + call.action;
        }
        call.result = null;
        return null;
    }

    /**
     * 从规则化的 /openapi 路径归一化出动作与表编码
     */
    private void resolveAction(OpenApiContext.Call call) {
        String[] segments = call.endpoint.split("/");
        if (call.endpoint.startsWith("/openapi/data/")) {
            String tableCode = segments.length > 3 ? segments[3] : null;
            String last = segments.length > 4 ? segments[4] : null;
            call.tableCode = tableCode;
            if (last != null && BODY_KEYWORD_ACTIONS.contains(last)) {
                call.action = "data:" + last;
            } else if ("GET".equalsIgnoreCase(call.httpMethod)) {
                call.action = "data:read";
            } else if ("DELETE".equalsIgnoreCase(call.httpMethod)) {
                call.action = "data:remove";
            } else {
                call.action = "data:unknown";
            }
            call.bizId = last;
        } else if (call.endpoint.startsWith("/openapi/flow/")) {
            call.action = "flow:start";
        } else if (call.endpoint.startsWith("/openapi/meta/")) {
            call.action = "meta:read";
        } else if (call.endpoint.startsWith("/openapi/schedule/")) {
 // 定时任务通道：写动作（注册/改期/取消）走 schedule:write，查询走 schedule:read
            if ("GET".equalsIgnoreCase(call.httpMethod)) {
                call.action = "schedule:read";
            } else {
                call.action = "schedule:write";
            }
        } else if (call.endpoint.startsWith("/openapi/ai/")) {
            // AI 开发一档通道：动作形如 ai:build:<op>，
            // 只挂只读/纯校验/草稿/试运行端点——二档/三档在该通道上"不存在"（构造性不可达）
            String sub = call.endpoint.substring("/openapi/ai/build/".length());
            call.action = "ai:build:" + (sub.isEmpty() ? "unknown" : sub.split("/")[0]);
        } else {
            call.action = "unknown";
        }
    }

    /**
     * 未识别的 action 一律返回拒绝哨兵而不是 null（§9.2 四处必改之一）：
     * 曾经的"return null → scope == null 时整个校验条件不成立 → 直接放行"是鉴权旁路，
     * 任何有效 AK/SK 都能调用未登记前缀的接口。哨兵不在任何凭证的 scope 集合里，
     * scopeAllows 必然 false → SCOPE_DENIED。
     */
    private static final String SCOPE_UNKNOWN_DENY = "unknown:deny";

    private String requiredScope(String action) {
        if (action == null) {
            return SCOPE_UNKNOWN_DENY;
        }
        if (action.startsWith("data:read") || action.startsWith("data:list")) {
            return OpenApiCredentialService.SCOPE_DATA_READ;
        }
        if (action.startsWith("data:add") || action.startsWith("data:edit") || action.startsWith("data:remove")) {
            return OpenApiCredentialService.SCOPE_DATA_WRITE;
        }
        if (action.startsWith("flow:")) {
            return OpenApiCredentialService.SCOPE_FLOW_START;
        }
        if (action.startsWith("meta:")) {
            return OpenApiCredentialService.SCOPE_META_READ;
        }
        if (action.startsWith("schedule:write")) {
            return OpenApiCredentialService.SCOPE_SCHEDULE_WRITE;
        }
        if (action.startsWith("schedule:read")) {
            return OpenApiCredentialService.SCOPE_SCHEDULE_READ;
        }
        if (action.startsWith("ai:build:")) {
            return OpenApiCredentialService.SCOPE_AI_BUILD;
        }
        return SCOPE_UNKNOWN_DENY;
    }

    /**
     * 授权比对用的是去掉动作前缀的白名单值（list/add/edit/remove）
     */
    private void finishAndRecord(OpenApiContext.Call call, HttpServletResponse response) {
        call.statusCode = response.getStatus();
        call.costMs = System.currentTimeMillis() - call.startMs;
        if (call.result == null) {
            boolean ok = response.getStatus() >= 200 && response.getStatus() < 300 && call.failMsg == null;
            call.result = ok ? OpenApiCallLogService.RESULT_SUCCESS : OpenApiCallLogService.RESULT_FAILED;
            if (!ok && call.errorCode == null) {
                call.errorCode = String.valueOf(response.getStatus());
            }
        }
        String errorMsg = StringUtils.isNotEmpty(call.denyMsg) ? call.denyMsg : call.failMsg;
        try {
            SystemOpenapiCallLog entry = new SystemOpenapiCallLog();
            entry.setRequestId(call.requestId);
            entry.setCredentialId(call.credential == null ? null : call.credential.getId());
            entry.setAppId(call.credential == null ? null : call.credential.getAppId());
            entry.setAccessKey(call.accessKey);
            entry.setEndpoint(call.endpoint);
            entry.setHttpMethod(call.httpMethod);
            entry.setAction(grantAction(call.action));
            entry.setTableCode(call.tableCode);
            entry.setBizId(call.bizId);
            entry.setResult(call.result);
            entry.setStatusCode(call.statusCode);
            entry.setErrorCode(call.errorCode);
            entry.setErrorMsg(errorMsg);
            entry.setCostMs(call.costMs);
            entry.setClientIp(call.clientIp);
            entry.setNonce(call.nonce);
            entry.setOrganizationId(call.credential == null ? null : call.credential.getOrganizationId());
            entry.setDepartmentId(call.credential == null ? null : call.credential.getBoundDepartmentId());
            entry.setCreatedTime(new Date());
            callLogService.recordQuietly(entry);
        } catch (Exception e) {
            log.warn("OpenAPI 流水构造失败 requestId={}", call.requestId, e);
        }
    }

    /** 白名单里的动作值不带 data: 前缀 */
    private String grantAction(String action) {
        if (action != null && action.startsWith("data:")) {
            return action.substring("data:".length());
        }
        return action;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotEmpty(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeError(HttpServletResponse response, OpenApiContext.Call call, String message) throws IOException {
        response.setStatus(OpenApiCallLogService.RESULT_RATE_LIMITED.equals(call.result) ? 429 : 401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(CommonResult.fail(CommonResult.AUTH_FAIL, message)));
    }
}
