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

import com.privernal.common.mybatis.domain.BaseEntity;
import com.privernal.common.mybatis.type.JSONObjectTypeHandler;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Webhook 事件 outbox system_webhook_event
 * <p>
 * 一行 = 一个「业务变更 × 订阅」的待投递项，重试状态按订阅隔离。
 */
@TableName(value = "system_webhook_event", autoResultMap = true)
@Data
public class SystemWebhookEvent extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String subscriptionId;

    private String eventType;

    private String tableCode;

    private String bizId;

    @TableField(typeHandler = JSONObjectTypeHandler.class)
    private JSONObject payload;

    /** PENDING / DELIVERING / SUCCESS / FAILED / DEAD */
    private String status;

    private Integer retryCount;

    private Date nextRetryTime;

    private String lastError;

    private Date deliveredTime;

    private String departmentId;

    private String organizationId;
}
