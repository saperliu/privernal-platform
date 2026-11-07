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
 * SSO 单点登录 OIDC 渠道配置 system_sso_oidc_config
 */
@TableName(value = "system_sso_oidc_config", autoResultMap = true)
@Data
public class SystemSsoOidcConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 登录页渠道标识，/auth/oidc/authorize/{configCode} */
    private String configCode;

    private String configName;

    private String icon;

    private String issuer;

    private String discoveryUrl;

    private String authorizeUrl;

    private String tokenUrl;

    private String userinfoUrl;

    private String jwksUri;

    private String clientId;

    private String clientSecretCipher;

    private String clientSecretPrefix;

    private String clientSecretLast4;

    private String scopes;

    private String redirectUri;

    @TableField(typeHandler = JSONObjectTypeHandler.class)
    private JSONObject claimMapping;

    private Integer autoRegister;

    private String defaultRoleIds;

    private Integer status;

    private String organizationId;

    private String departmentId;
}
