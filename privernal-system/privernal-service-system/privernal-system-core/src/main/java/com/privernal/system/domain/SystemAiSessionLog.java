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
 * AI 会话审计日志。
 * <p>
 * 每个带 X-AI-Session 标记的 /back/*、/dbs/* 请求落一行（action=ALLOW/DENY），
 * 编排层会话开始/结束时落 START/CLOSE（控制器线程写，带完整身份）。
 * 会话的工具调用序列 = 按 created_time 排序的请求序列（每个工具调用即一次 HTTP 请求）。
 */
@Data
@TableName("system_ai_session_log")
public class SystemAiSessionLog implements Serializable {

    public static final String ACTION_START = "START";
    public static final String ACTION_CLOSE = "CLOSE";
    public static final String ACTION_ALLOW = "ALLOW";
    public static final String ACTION_DENIED = "DENIED";
    public static final String ACTION_CONFIG_FETCH = "CONFIG_FETCH";

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** AI 会话号（X-AI-Session） */
    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private String userId;

    @TableField("user_name")
    private String userName;

    @TableField("organization_id")
    private String organizationId;

    /** START/CLOSE/ALLOW/DENIED/CONFIG_FETCH */
    @TableField("action")
    private String action;

    @TableField("http_method")
    private String httpMethod;

    @TableField("request_uri")
    private String requestUri;

    @TableField("deny_reason")
    private String denyReason;

    @TableField("created_time")
    private Date createdTime;
}
