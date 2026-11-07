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
 * OpenAPI 凭证查询过滤器
 */
@Tag(name = "OpenApiCredentialFilterDTO", description = "OpenAPI凭证查询过滤器")
@Data
public class OpenApiCredentialFilterDTO extends FilterDTO {

    private static final long serialVersionUID = 1L;

    @Schema(description = "归属应用ID")
    private String appId;

    @Schema(description = "凭证名称（模糊）")
    private String credentialName;

    @Schema(description = "AccessKey")
    private String accessKey;

    @Schema(description = "状态;1=启用,0=停用")
    private Integer status;

    @Schema(description = "所属组织")
    private String organizationId;
}
