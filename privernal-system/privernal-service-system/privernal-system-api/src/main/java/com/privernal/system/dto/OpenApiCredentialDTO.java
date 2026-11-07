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
import java.util.List;

/**
 * OpenAPI 应用凭证 DTO，对应 system_openapi_credential
 */
@Tag(name = "OpenApiCredentialDTO", description = "OpenAPI应用凭证")
@Data
public class OpenApiCredentialDTO extends BaseDTO {

    private static final long serialVersionUID = 1L;

    private String id;

    @Schema(description = "归属应用ID")
    private String appId;

    @Schema(description = "归属应用名称（查询回填，只读）")
    private String appName;

    @Schema(description = "凭证名称")
    private String credentialName;

    @Schema(description = "AccessKey，请求头 X-Access-Key")
    private String accessKey;

    /**
     * 明文 Secret 只在「新建」与「轮换」那一次响应里出现，其余任何查询都保持 null
     */
    @Schema(description = "明文Secret，仅新建/轮换响应返回一次，请勿落库或落日志")
    private String secret;

    @Schema(description = "Secret前缀，列表展示用")
    private String secretPrefix;

    @Schema(description = "Secret末4位，列表展示用")
    private String secretLast4;

    @Schema(description = "API范围;data:read/data:write/meta:read/flow:start")
    private List<String> scopes;

    @Schema(description = "按表授权")
    private List<OpenApiTableGrantDTO> tableGrants;

    @Schema(description = "数据归属用户ID（写数据/发起流程的身份来源，非权限来源）")
    private String boundUserId;

    @Schema(description = "数据归属部门ID")
    private String boundDepartmentId;

    @Schema(description = "状态;1=启用,0=停用")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "过期时间，空=永不过期")
    private Date expireTime;

    @Schema(description = "每分钟调用额度")
    private Integer rateLimitPerMinute;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "最近调用时间")
    private Date lastCallTime;

    @Schema(description = "累计调用次数")
    private Long callCount;

    @Schema(description = "所属组织")
    private String organizationId;
}
