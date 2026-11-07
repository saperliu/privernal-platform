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

package com.privernal.dynamic.advanced.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 外链访问审计日志（§5.3-2）：append-only 大表，无软删列、无更新语义。
 * 对外 404 不区分原因（防探测），这里落内部原因供管理端排查。
 */
@Data
@TableName(value = "dynamic_share_access_log")
public class DynamicShareAccessLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String organizationId;

    private String shareId;

    private String formId;

    /** META预检/SUBMIT提交/UPDATE回执修改/VIEW_LIST/VIEW_DETAIL */
    private String action;

    /** token 尾号（不落原文） */
    private String tokenTail;

    private String clientIp;

    private String userAgent;

    /** SUCCESS/DENIED/REJECTED */
    private String result;

    /** 拒绝原因（仅内部可查） */
    private String denyReason;

    /** 关联数据行（提交/回执修改/详情时） */
    private String dataId;

    /** 操作人（管理端动作如 REVEAL 取链时记录；匿名入口为空） */
    private String operatorId;

    private Date createdTime;

    public static final String ACTION_META = "META";
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_VIEW_LIST = "VIEW_LIST";
    public static final String ACTION_VIEW_DETAIL = "VIEW_DETAIL";
    /** 管理端取链回显（每次解密返回完整链接都留痕） */
    public static final String ACTION_REVEAL = "REVEAL";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_DENIED = "DENIED";
    public static final String RESULT_REJECTED = "REJECTED";
}
