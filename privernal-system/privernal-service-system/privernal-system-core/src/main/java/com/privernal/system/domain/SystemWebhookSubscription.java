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

/**
 * Webhook 事件订阅 system_webhook_subscription
 */
@TableName(value = "system_webhook_subscription", autoResultMap = true)
@Data
public class SystemWebhookSubscription extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String appId;

    private String subscriptionName;

    private String url;

    private String httpMethod;

    /** 签名密钥密文，投递时算 X-Webhook-Signature；与凭证同理不能存 hash */
    private String secretCipher;

    private String secretPrefix;

    private String secretLast4;

    @TableField(typeHandler = JSONObjectTypeHandler.class)
    private JSONObject events;

    /** 非空则只收该表的事件 */
    private String tableCode;

    @TableField(typeHandler = JSONObjectTypeHandler.class)
    private JSONObject headers;

    private Integer sslVerify;

    private Integer timeoutMs;

    private Integer maxRetry;

    private Integer status;

    private String organizationId;

    private String departmentId;
}
