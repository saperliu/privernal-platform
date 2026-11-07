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
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.util.Date;

/**
 * Webhook 投递记录 DTO，对应 system_webhook_delivery_log
 */
@Tag(name = "WebhookDeliveryLogDTO", description = "Webhook投递记录")
@Data
public class WebhookDeliveryLogDTO extends BaseDTO {

    private static final long serialVersionUID = 1L;

    private String id;

    @Schema(description = "事件ID")
    private String eventId;

    @Schema(description = "订阅ID")
    private String subscriptionId;

    @Schema(description = "第几次尝试")
    private Integer attempt;

    @Schema(description = "实际请求地址")
    private String url;

    @Schema(description = "响应状态码")
    private Integer statusCode;

    @Schema(description = "是否成功;1=成功,0=失败")
    private Integer success;

    @Schema(description = "响应内容摘要")
    private String responseBrief;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "耗时毫秒")
    private Long costMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "投递时间")
    private Date createdTime;
}
