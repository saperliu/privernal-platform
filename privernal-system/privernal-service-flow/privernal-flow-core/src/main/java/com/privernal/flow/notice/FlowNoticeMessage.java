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

package com.privernal.flow.notice;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 流程通知消息（由 FlowMessageNotifyListener 组装，交由 FlowNoticeSender 发送）
 *
 * @author saperliu
 */
@Data
public class FlowNoticeMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 触发的事件类型（FlowEvent.TYPE_*） */
    private String eventType;

    /** 流程实例id */
    private Long instanceId;

    /** 业务数据id */
    private String businessId;

    /** 流程编码 */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 前端跳转地址（可空） */
    private String detailUrl;

    /** 收件人用户id列表 */
    private List<String> userIds;
}
