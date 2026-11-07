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

package com.privernal.system.dto;

import com.privernal.common.web.domain.BaseDTO;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.util.List;

/**
 * Webhook 订阅 DTO，对应 system_webhook_subscription
 */
@Tag(name = "WebhookSubscriptionDTO", description = "Webhook事件订阅")
@Data
public class WebhookSubscriptionDTO extends BaseDTO {

    private static final long serialVersionUID = 1L;

    private String id;

    @Schema(description = "归属应用ID")
    private String appId;

    @Schema(description = "订阅名称")
    private String subscriptionName;

    @Schema(description = "回调地址")
    private String url;

    @Schema(description = "请求方法，默认POST")
    private String httpMethod;

    /**
     * 明文签名密钥只在「新建」与「轮换」那一次响应里出现
     */
    @Schema(description = "明文签名密钥，仅新建/轮换响应返回一次")
    private String secret;

    @Schema(description = "密钥前缀，展示用")
    private String secretPrefix;

    @Schema(description = "密钥末4位，展示用")
    private String secretLast4;

    @Schema(description = "订阅的事件类型码")
    private List<String> events;

    @Schema(description = "仅该表的事件，空=本组织全部表")
    private String tableCode;

    @Schema(description = "附加请求头")
    private JSONObject headers;

    @Schema(description = "是否校验TLS证书;1=校验,0=不校验")
    private Integer sslVerify;

    @Schema(description = "连接/读取超时毫秒")
    private Integer timeoutMs;

    @Schema(description = "最大重试次数")
    private Integer maxRetry;

    @Schema(description = "状态;1=启用,0=停用")
    private Integer status;

    @Schema(description = "所属组织")
    private String organizationId;
}
