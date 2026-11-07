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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * 社交账号对象 system_social_user
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 社交账号对象 system_social_user
 */
@Tag(name = "SystemSocialUserDTO", description = "社交账号对应的DTO")
@Data
public class SystemSocialUserDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     */
    @Schema(description = "")

    private String id;

    /**
     * 第三方系统的唯一ID
     */
    @Schema(description = "第三方系统的唯一ID")

    private String uuid;

    /**
     * 第三方用户来源 GITHUB、GITEE、QQ
     */
    @Schema(description = "第三方用户来源 GITHUB、GITEE、QQ")

    private String source;

    /**
     * 应用的编码,区分哪个应用登录
     */
    @Schema(description = "应用的编码,区分哪个应用登录")

    private String appCode;

    /**
     * 用户的授权令牌
     */
    @Schema(description = "用户的授权令牌")

    private String accessToken;

    /**
     * 第三方用户的授权令牌的有效期
     */
    @Schema(description = "第三方用户的授权令牌的有效期")

    private Long expireIn;

    /**
     * 刷新令牌
     */
    @Schema(description = "刷新令牌")

    private String refreshToken;

    /**
     * 第三方用户的 open id
     */
    @Schema(description = "第三方用户的 open id")

    private String openId;

    /**
     * 第三方用户的 ID
     */
    @Schema(description = "第三方用户的 ID")

    private String uid;

    /**
     * 个别平台的授权信息
     */
    @Schema(description = "个别平台的授权信息")

    private String accessCode;

    /**
     * 第三方用户的 union id
     */
    @Schema(description = "第三方用户的 union id")

    private String unionId;

    /**
     * 第三方用户授予的权限
     */
    @Schema(description = "第三方用户授予的权限")

    private String scope;

    /**
     * 个别平台的授权信息
     */
    @Schema(description = "个别平台的授权信息")

    private String tokenType;

    /**
     * id token
     */
    @Schema(description = "id token")

    private String idToken;

    /**
     * 小米平台用户的附带属性
     */
    @Schema(description = "小米平台用户的附带属性")

    private String macAlgorithm;

    /**
     * 小米平台用户的附带属性
     */
    @Schema(description = "小米平台用户的附带属性")

    private String macKey;

    /**
     * 用户的授权code
     */
    @Schema(description = "用户的授权code")

    private String code;

    /**
     * Twitter平台用户的附带属性
     */
    @Schema(description = "Twitter平台用户的附带属性")

    private String oauthToken;

    /**
     * Twitter平台用户的附带属性
     */
    @Schema(description = "Twitter平台用户的附带属性")

    private String oauthTokenSecret;

    /**
     * 所属组织
     */
    @Schema(description = "所属组织")

    private String organizationId;

    /**
     * 所属部门
     */
    @Schema(description = "所属部门")
    private String departmentId;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "用户的微信昵称")
    private String nickName;

    @Schema(description = "用户的头像url")
    private String avatarUrl;

    @Schema(description = "用户的邮箱")
    private String email;

    /**
     * 纯手机号
     */
    @Schema(description = "纯手机号")
    private String purePhoneNumber;

    /**
     * 区号
     */
    @Schema(description = "区号")
    private String countryCode;
    /**
     * 对应的角色
     */
    @Schema(description = "对应的角色")
    private String roleId;

    /**
     * 是否要审核用户  Y 是   N 否
     */
    @Schema(description = "用户是否需要审核")
    private String examineUser;

    @Schema(description = "国家")
    private String country;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    /**
     * 创建应用的agentId
     */
    @Schema(description = "创建应用的agentId")
    private String agentId;

    /**
     * 创建应用的scheme
     */
    @Schema(description = "创建应用的scheme")
    private String scheme;

    /**
     * 创建应用的企业ID
     */
    @Schema(description = "创建应用的企业ID")
    private String corpId;
}
