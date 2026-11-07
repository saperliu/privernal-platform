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

package com.privernal.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 问数对话消息（对话历史落库）。
 * <p>
 * 编排层在 done 后回写 user 原文与 assistant 终文（fire-and-forget，写失败不阻断对话）；
 * 前端历史回看直连后端读，不经编排层。
 *
 * @author saperliu
 */
@Data
@TableName("system_ai_chat_message")
public class SystemAiChatMessage implements Serializable {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** AI 会话号（X-AI-Session；问数页侧栏按会话聚合） */
    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private String userId;

    @TableField("user_name")
    private String userName;

    @TableField("organization_id")
    private String organizationId;

    /** user / assistant */
    @TableField("role")
    private String role;

    /** 消息正文（user=原文，assistant=终文） */
    @TableField("content")
    private String content;

    /** 本条消息产出的内联图表 JSON 数组（chart SSE 事件 payloads；空=null） */
    @TableField("charts")
    private String charts;

    @TableField("created_time")
    private Date createdTime;
}
