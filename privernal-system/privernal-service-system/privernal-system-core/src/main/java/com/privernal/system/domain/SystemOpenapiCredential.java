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

import java.util.Date;

/**
 * OpenAPI 应用凭证 system_openapi_credential
 */
@TableName(value = "system_openapi_credential", autoResultMap = true)
@Data
public class SystemOpenapiCredential extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 归属应用，复用 system_app_info，一应用可多凭证 */
    private String appId;

    private String credentialName;

    /** 公开标识，请求头 X-Access-Key */
    private String accessKey;

    /**
     * Secret 的 AES/GCM 密文。
     * ⚠️ 这里刻意不是 sha256：验签要求服务端能用同一密钥重算 HMAC，哈希后无法还原。
 * GitHub PAT 那类"存 hash"之所以可行，是因为它只做等值比对、不做签名。改这列前先读 。
     */
    private String secretCipher;

    /** sha256(明文) hex，用于掩码展示与查重，不参与验签 */
    private String secretFingerprint;

    private String secretPrefix;

    private String secretLast4;

    @TableField(typeHandler = JSONObjectTypeHandler.class)
    private JSONObject scopes;

    @TableField(typeHandler = JSONObjectTypeHandler.class)
    private JSONObject tableGrant;

    /** 数据归属用户：写动态数据时补 created_by、发起流程时的发起人；不作为权限来源 */
    private String boundUserId;

    private String boundDepartmentId;

    private Integer status;

    private Date expireTime;

    private Integer rateLimitPerMinute;

    private Date lastCallTime;

    private Long callCount;

    private String organizationId;

    /** NOT NULL 且无默认值，必须由实体存在才能被 EntityHandler 自动填充 */
    private String departmentId;
}
