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
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Webhook 事件（outbox）DTO，对应 system_webhook_event
 */
@Tag(name = "WebhookEventDTO", description = "Webhook事件")
@Data
public class WebhookEventDTO extends BaseDTO {

    private static final long serialVersionUID = 1L;

    private String id;

    @Schema(description = "订阅ID")
    private String subscriptionId;

    @Schema(description = "订阅名称（查询回填，只读）")
    private String subscriptionName;

    @Schema(description = "事件类型码")
    private String eventType;

    @Schema(description = "动态表编码")
    private String tableCode;

    @Schema(description = "业务数据ID")
    private String bizId;

    @Schema(description = "事件内容，投递时原样作为请求体")
    private JSONObject payload;

    @Schema(description = "状态;PENDING/DELIVERING/SUCCESS/FAILED/DEAD")
    private String status;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "下次可投递时间")
    private Date nextRetryTime;

    @Schema(description = "最近一次失败原因")
    private String lastError;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "成功送达时间")
    private Date deliveredTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "事件产生时间")
    private Date createdTime;

    @Schema(description = "所属组织")
    private String organizationId;

    @Schema(description = "投递记录（详情接口返回，列表不返回）")
    private List<WebhookDeliveryLogDTO> deliveryLogs;
}
