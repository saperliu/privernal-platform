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

package com.privernal.system.filter;

import com.privernal.common.web.domain.FilterDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * Webhook 事件查询过滤器
 */
@Tag(name = "WebhookEventFilterDTO", description = "Webhook事件查询过滤器")
@Data
public class WebhookEventFilterDTO extends FilterDTO {

    private static final long serialVersionUID = 1L;

    @Schema(description = "订阅ID")
    private String subscriptionId;

    @Schema(description = "事件类型码")
    private String eventType;

    @Schema(description = "动态表编码")
    private String tableCode;

    @Schema(description = "业务数据ID")
    private String bizId;

    @Schema(description = "状态;PENDING/DELIVERING/SUCCESS/FAILED/DEAD")
    private String status;

    @Schema(description = "起始时间 yyyy-MM-dd HH:mm:ss")
    private String beginTime;

    @Schema(description = "结束时间 yyyy-MM-dd HH:mm:ss")
    private String endTime;

    @Schema(description = "所属组织")
    private String organizationId;
}
