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

package com.privernal.system.controller;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.api.ISecretCipherService;
import com.privernal.system.domain.SystemAiConfig;
import com.privernal.system.service.ISystemAiConfigService;
import com.privernal.system.service.SystemAiSessionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 搭建编排层服务端：privernal-ai-orchestrator 的唯一后端依赖面。
 * <p>
 * ① {@code GET /back/system/ai/ai/build/config}：取本组织生效模型配置（含解密 apiKey）——
 * 编排层用它调所配模型（公网/内网）。权限独立成 system:aiBuild:use（能看掩码 ≠ 能取明文），
 * 每次调用落 CONFIG_FETCH 审计行；apiKey 只在响应里出现一次，不落日志。
 * ② {@code POST /back/system/ai/ai/build/session}：编排层会话登记（START 行带完整登录身份，
 * Filter 线程的 ALLOW/DENIED 行身份 best-effort，权威身份以此为准）。
 */
@Tag(name = "AI 搭建编排层")
@Slf4j
@RestController
@RequestMapping("/back/system/ai")
public class SystemAiBuildController {

    @Autowired
    private ISystemAiConfigService aiConfigService;

    @Autowired
    private SystemAiSessionLogService sessionLogService;

    @Autowired
    private com.privernal.system.service.SystemAiChatMessageService chatMessageService;

    @Autowired(required = false)
    private ISecretCipherService secretCipherService;

    @Operation(summary = "取生效模型配置（含 apiKey 明文，编排层专用；每次调用留审计）")
    @PreAuthorize(hasAnyPermi = {"system:aiBuild:use", "system:aiAnalyze:use"})
    @GetMapping("/ai/build/config")
    public CommonResult<Map<String, Object>> buildConfig() {
        SystemAiConfig config = aiConfigService.effectiveConfig();
        if (config == null) {
            throw new ServiceException("本组织尚未启用 AI 模型配置，请先在「AI 模型配置」页保存并启用");
        }
        Map<String, Object> out = new HashMap<>();
        out.put("baseUrl", config.getBaseUrl());
        out.put("model", config.getModel());
        out.put("temperature", config.getTemperature());
        out.put("sessionTokenLimit", config.getSessionTokenLimit());
        out.put("intranetFlag", config.getIntranetFlag());
        out.put("dataFlowNotice", SystemAiConfig.DATA_FLOW_NOTICE);
        if (StringUtils.isNotEmpty(config.getApiKeyCipher())) {
            if (secretCipherService == null || !secretCipherService.isConfigured()) {
                throw new ServiceException("未配置 openapi.secret-master-key，无法解密 apiKey（fail-closed，不降级）");
            }
            out.put("apiKey", secretCipherService.decrypt(config.getApiKeyCipher()));
        }
        return CommonResult.ok(out);
    }

    @Operation(summary = "编排层会话登记（幂等；START 行记录发起人身份）")
    @PreAuthorize(hasAnyPermi = {"system:aiBuild:use", "system:aiAnalyze:use"})
    @PostMapping("/ai/build/session")
    public CommonResult<Boolean> register(@RequestBody Map<String, String> payload) {
        sessionLogService.register(payload == null ? null : payload.get("sessionId"));
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "编排层会话关闭（CLOSE 留痕）")
    @PreAuthorize(hasAnyPermi = {"system:aiBuild:use", "system:aiAnalyze:use"})
    @PostMapping("/ai/build/session/close")
    public CommonResult<Boolean> close(@RequestBody Map<String, String> payload) {
        sessionLogService.close(payload == null ? null : payload.get("sessionId"));
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "本会话审计序列（工具调用轨迹 / DENIED 明细）")
    @PreAuthorize(hasPermi = "system:aiBuild:use")
    @GetMapping("/ai/build/session/logs")
    public CommonResult<Object> sessionLogs(String sessionId) {
        if (StringUtils.isEmpty(sessionId)) {
            throw new ServiceException("sessionId 不能为空");
        }
        return CommonResult.ok(sessionLogService.listBySession(sessionId));
    }

    // -------------------------------------------------------------------------
    // 问数对话历史落库。编排层 done 后回写（fire-and-forget）；
    // 前端历史回看直连后端读。权限两键并存：build 页 system:aiBuild:use、问数页 system:aiAnalyze:use。
    // -------------------------------------------------------------------------

    @Operation(summary = "回写一条问数对话消息（编排层 done 后调用；fire-and-forget）")
    @PreAuthorize(hasAnyPermi = {"system:aiBuild:use", "system:aiAnalyze:use"})
    @PostMapping("/ai/chat/message")
    public CommonResult<Boolean> appendChatMessage(@RequestBody Map<String, Object> payload) {
        if (payload == null) {
            return CommonResult.ok(Boolean.TRUE);
        }
        chatMessageService.append(
                (String) payload.get("sessionId"),
                (String) payload.get("role"),
                (String) payload.get("content"),
                payload.get("charts") == null ? null
                        : String.valueOf(payload.get("charts")));
        return CommonResult.ok(Boolean.TRUE);
    }

    @Operation(summary = "按会话回放问数对话消息（只回本人消息）")
    @PreAuthorize(hasAnyPermi = {"system:aiBuild:use", "system:aiAnalyze:use"})
    @GetMapping("/ai/chat/messages")
    public CommonResult<Object> chatMessages(String sessionId) {
        if (StringUtils.isEmpty(sessionId)) {
            throw new ServiceException("sessionId 不能为空");
        }
        return CommonResult.ok(chatMessageService.listBySession(sessionId));
    }

    @Operation(summary = "当前登录人的问数历史会话侧栏（按组织+用户隔离，最近在前）")
    @PreAuthorize(hasAnyPermi = {"system:aiBuild:use", "system:aiAnalyze:use"})
    @GetMapping("/ai/chat/sessions")
    public CommonResult<Object> chatSessions() {
        return CommonResult.ok(chatMessageService.listSessions());
    }
}
