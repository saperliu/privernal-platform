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

package com.privernal.system.service;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.domain.SystemAiSessionLog;
import com.privernal.system.mapper.SystemAiSessionLogMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * AI 会话审计：X-AI-Session 请求留痕 + 会话登记/关闭。
 * <p>
 * 写入一律 best-effort：审计失败只打日志，绝不影响业务请求（Filter 线程上无完整登录态，
 * 身份字段可能为空，权威身份以编排层登记的 START 行为准）。
 */
@Slf4j
@Service
public class SystemAiSessionLogService {

    @Autowired
    private SystemAiSessionLogMapper sessionLogMapper;

    /** Filter 线程留痕：身份字段 best-effort（可能为空，START 行兜底） */
    public void logRequest(String sessionId, String action, String method, String uri, String denyReason) {
        try {
            SystemAiSessionLog row = new SystemAiSessionLog();
            row.setSessionId(sessionId);
            row.setAction(action);
            row.setHttpMethod(method);
            row.setRequestUri(uri);
            row.setDenyReason(denyReason);
            row.setCreatedTime(new Date());
            fillIdentityBestEffort(row);
            sessionLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("AI 会话审计写入失败 session={} action={} uri={}", sessionId, action, uri, e);
        }
    }

    /** 编排层会话登记（控制器线程，带完整身份）；重复登记幂等跳过 */
    public void register(String sessionId) {
        if (StringUtils.isEmpty(sessionId)) {
            return;
        }
        try {
            Long exists = sessionLogMapper.selectCount(Wrappers.<SystemAiSessionLog>lambdaQuery()
                    .eq(SystemAiSessionLog::getSessionId, sessionId)
                    .eq(SystemAiSessionLog::getAction, SystemAiSessionLog.ACTION_START));
            if (exists != null && exists > 0) {
                return;
            }
            SystemAiSessionLog row = new SystemAiSessionLog();
            row.setSessionId(sessionId);
            row.setAction(SystemAiSessionLog.ACTION_START);
            row.setCreatedTime(new Date());
            fillIdentityBestEffort(row);
            sessionLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("AI 会话登记失败 session={}", sessionId, e);
        }
    }

    public void close(String sessionId) {
        logRequest(sessionId, SystemAiSessionLog.ACTION_CLOSE, null, null, null);
    }

    /** 按会话查审计（工具调用序列 = ALLOW/DENIED 按时间排序） */
    public List<SystemAiSessionLog> listBySession(String sessionId) {
        return sessionLogMapper.selectList(Wrappers.<SystemAiSessionLog>lambdaQuery()
                .eq(SystemAiSessionLog::getSessionId, sessionId)
                .orderByAsc(SystemAiSessionLog::getCreatedTime));
    }

    private void fillIdentityBestEffort(SystemAiSessionLog row) {
        try {
            row.setUserId(SecurityUtils.getUserId());
            row.setUserName(SecurityUtils.getNickName());
            row.setOrganizationId(SecurityUtils.getOrganizationId());
        } catch (Exception ignore) {
            // 无会话线程（Filter 早于鉴权链）身份留空，START 行兜底
        }
    }
}
