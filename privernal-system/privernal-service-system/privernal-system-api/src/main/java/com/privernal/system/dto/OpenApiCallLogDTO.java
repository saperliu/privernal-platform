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
 * OpenAPI 调用流水 DTO，对应 system_openapi_call_log
 */
@Tag(name = "OpenApiCallLogDTO", description = "OpenAPI调用流水")
@Data
public class OpenApiCallLogDTO extends BaseDTO {

    private static final long serialVersionUID = 1L;

    private String id;

    @Schema(description = "调用追踪号")
    private String requestId;

    @Schema(description = "凭证ID")
    private String credentialId;

    @Schema(description = "应用ID")
    private String appId;

    @Schema(description = "AccessKey")
    private String accessKey;

    @Schema(description = "请求PATH")
    private String endpoint;

    @Schema(description = "请求METHOD")
    private String httpMethod;

    @Schema(description = "归一化动作")
    private String action;

    @Schema(description = "目标动态表编码")
    private String tableCode;

    @Schema(description = "业务数据ID")
    private String bizId;

    @Schema(description = "结果;SUCCESS/DENIED/FAILED/RATE_LIMITED")
    private String result;

    @Schema(description = "HTTP状态码")
    private Integer statusCode;

    @Schema(description = "错误码")
    private String errorCode;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "耗时毫秒")
    private Long costMs;

    @Schema(description = "调用方IP")
    private String clientIp;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "调用时间")
    private Date createdTime;

    @Schema(description = "所属组织")
    private String organizationId;
}
