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
 * OpenAPI 调用流水查询过滤器
 */
@Tag(name = "OpenApiCallLogFilterDTO", description = "OpenAPI调用流水查询过滤器")
@Data
public class OpenApiCallLogFilterDTO extends FilterDTO {

    private static final long serialVersionUID = 1L;

    @Schema(description = "凭证ID")
    private String credentialId;

    @Schema(description = "应用ID")
    private String appId;

    @Schema(description = "AccessKey")
    private String accessKey;

    @Schema(description = "归一化动作")
    private String action;

    @Schema(description = "目标动态表编码")
    private String tableCode;

    @Schema(description = "结果;SUCCESS/DENIED/FAILED/RATE_LIMITED")
    private String result;

    @Schema(description = "起始时间 yyyy-MM-dd HH:mm:ss")
    private String beginTime;

    @Schema(description = "结束时间 yyyy-MM-dd HH:mm:ss")
    private String endTime;

    @Schema(description = "所属组织")
    private String organizationId;
}
