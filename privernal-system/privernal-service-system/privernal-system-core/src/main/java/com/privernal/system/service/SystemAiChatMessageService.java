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
import com.privernal.system.domain.SystemAiChatMessage;
import com.privernal.system.mapper.SystemAiChatMessageMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 问数对话消息服务（B8 Chat BI M2）。
 * <p>
 * 写入方是编排层（done 后回写，fire-and-forget）：失败只记日志不抛错，对话不被历史
 * 落库阻断。读取方是前端历史回看（直连后端）。隔离口径：组织 + 用户——问数历史是
 * 个人工作痕迹，同组织其他人（含管理员）不互看。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class SystemAiChatMessageService {

    /** 历史会话侧栏最多回看的会话数（超出按最近活跃截断） */
    public static final int MAX_SESSIONS = 50;

    @Autowired
    private SystemAiChatMessageMapper chatMessageMapper;

    /** 编排层回写一条消息（fire-and-forget：任何失败只告警） */
    public void append(String sessionId, String role, String content, String charts) {
        if (StringUtils.isEmpty(sessionId) || StringUtils.isEmpty(content)) {
            return;
        }
        if (!SystemAiChatMessage.ROLE_USER.equals(role)
                && !SystemAiChatMessage.ROLE_ASSISTANT.equals(role)) {
            return;
        }
        try {
            SystemAiChatMessage row = new SystemAiChatMessage();
            row.setSessionId(sessionId);
            row.setRole(role);
            row.setContent(content);
            row.setCharts(StringUtils.isEmpty(charts) ? null : charts);
            row.setCreatedTime(new Date());
            fillIdentityBestEffort(row);
            chatMessageMapper.insert(row);
        } catch (Exception e) {
            log.warn("AI 问数消息落库失败 session={} role={}", sessionId, role, e);
        }
    }

    /**
     * 按会话回放消息（时间正序）。fail-closed：取不到当前登录身份时返回空列表——
     * 消息按组织+用户隔离，绝不能在无身份线程上放行全表。
     */
    public List<SystemAiChatMessage> listBySession(String sessionId) {
        if (StringUtils.isEmpty(sessionId)) {
            return new ArrayList<>();
        }
        String userId = currentUserIdOrNull();
        if (userId == null) {
            return new ArrayList<>();
        }
        return chatMessageMapper.selectList(Wrappers.<SystemAiChatMessage>lambdaQuery()
                .eq(SystemAiChatMessage::getSessionId, sessionId)
                .eq(SystemAiChatMessage::getUserId, userId)
                .orderByAsc(SystemAiChatMessage::getCreatedTime));
    }

    /**
     * 当前登录人的历史会话侧栏：按组织+用户过滤（问数历史是个人工作痕迹，同组织他人不互看），
     * 最近会话在前。title 取该会话第一条 user 消息的前 40 字符；消息量小（个人对话），聚合在内存做。
     */
    public List<Map<String, Object>> listSessions() {
        String userId = currentUserIdOrNull();
        if (userId == null) {
            return new ArrayList<>();
        }
        List<SystemAiChatMessage> rows = chatMessageMapper.selectList(
                Wrappers.<SystemAiChatMessage>lambdaQuery()
                        .eq(SystemAiChatMessage::getUserId, userId)
                        .orderByAsc(SystemAiChatMessage::getCreatedTime));
        Map<String, Map<String, Object>> sessions = new LinkedHashMap<>();
        for (SystemAiChatMessage row : rows) {
            Map<String, Object> s = sessions.computeIfAbsent(row.getSessionId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sessionId", k);
                m.put("createdTime", row.getCreatedTime());
                m.put("messageCount", 0);
                m.put("title", "");
                return m;
            });
            s.put("messageCount", (Integer) s.get("messageCount") + 1);
            s.put("createdTime", row.getCreatedTime());
            if (StringUtils.isEmpty((String) s.get("title"))
                    && SystemAiChatMessage.ROLE_USER.equals(row.getRole())) {
                String content = row.getContent();
                s.put("title", content.length() > 40 ? content.substring(0, 40) : content);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(sessions.values());
        // 会话按 map 顺序即首条消息时间正序；侧栏要最近在前，反转后截断
        java.util.Collections.reverse(out);
        return out.size() > MAX_SESSIONS ? new ArrayList<>(out.subList(0, MAX_SESSIONS)) : out;
    }

    /** 读路径的隔离键：取不到登录身份返回 null（调用方必须 fail-closed 返回空） */
    private String currentUserIdOrNull() {
        try {
            String userId = SecurityUtils.getUserId();
            return StringUtils.isEmpty(userId) ? null : userId;
        } catch (Exception e) {
            return null;
        }
    }

    private void fillIdentityBestEffort(SystemAiChatMessage row) {
        try {
            row.setUserId(SecurityUtils.getUserId());
            row.setUserName(SecurityUtils.getNickName());
            row.setOrganizationId(SecurityUtils.getOrganizationId());
        } catch (Exception ignore) {
            // 无登录上下文（编排层直调理论上带 token，不会走到这里）；留空不影响回放
        }
    }
}
