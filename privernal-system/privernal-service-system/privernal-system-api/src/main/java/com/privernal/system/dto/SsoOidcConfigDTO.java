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

/**
 * SSO OIDC 渠道配置 DTO，对应 system_sso_oidc_config
 */
@Tag(name = "SsoOidcConfigDTO", description = "SSO单点登录OIDC配置")
@Data
public class SsoOidcConfigDTO extends BaseDTO {

    private static final long serialVersionUID = 1L;

    private String id;

    @Schema(description = "渠道编码，登录页标识")
    private String configCode;

    @Schema(description = "渠道名称")
    private String configName;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "Issuer")
    private String issuer;

    @Schema(description = "发现文档地址，留空则由 issuer 推导")
    private String discoveryUrl;

    @Schema(description = "授权端点")
    private String authorizeUrl;

    @Schema(description = "令牌端点")
    private String tokenUrl;

    @Schema(description = "用户信息端点")
    private String userinfoUrl;

    @Schema(description = "JWKS地址")
    private String jwksUri;

    @Schema(description = "客户端ID")
    private String clientId;

    /**
     * 明文密钥只在保存那一次响应里回显，查询接口一律为 null
     */
    @Schema(description = "客户端密钥明文，仅保存响应返回")
    private String clientSecret;

    @Schema(description = "密钥前缀，展示用")
    private String clientSecretPrefix;

    @Schema(description = "密钥末4位，展示用")
    private String clientSecretLast4;

    @Schema(description = "请求scope，空格分隔")
    private String scopes;

    @Schema(description = "回调地址")
    private String redirectUri;

    @Schema(description = "声明映射")
    private JSONObject claimMapping;

    @Schema(description = "首次登录是否自动建用户;1=建,0=仅允许已绑定")
    private Integer autoRegister;

    @Schema(description = "自动建用户授予的角色ID，逗号分隔")
    private String defaultRoleIds;

    @Schema(description = "状态;1=启用,0=停用")
    private Integer status;

    @Schema(description = "所属组织")
    private String organizationId;
}
