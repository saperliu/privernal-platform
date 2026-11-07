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
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * 社交账号对象 system_social_user
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 社交账号的实体
 */
@Data
public class SystemSocialUser extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 第三方系统的唯一ID
     */
    private String uuid;
    /**
     * 第三方用户来源 GITHUB、GITEE、QQ
     */
    private String source;
    /**
     * 应用的编码,区分哪个应用登录
     */
    private String appCode;
    /**
     * 用户的授权令牌
     */
    private String accessToken;
    /**
     * 第三方用户的授权令牌的有效期
     */
    private Long expireIn;
    /**
     * 刷新令牌
     */
    private String refreshToken;
    /**
     * 第三方用户的 open id
     */
    private String openId;
    /**
     * 第三方用户的 ID
     */
    private String uid;
    /**
     * 个别平台的授权信息
     */
    private String accessCode;
    /**
     * 第三方用户的 union id
     */
    private String unionId;
    /**
     * 第三方用户授予的权限
     */
    private String scope;
    /**
     * 个别平台的授权信息
     */
    private String tokenType;
    /**
     * id token
     */
    private String idToken;
    /**
     * 小米平台用户的附带属性
     */
    private String macAlgorithm;
    /**
     * 小米平台用户的附带属性
     */
    private String macKey;
    /**
     * 用户的授权code
     */
    private String code;
    /**
     * Twitter平台用户的附带属性
     */
    private String oauthToken;
    /**
     * Twitter平台用户的附带属性
     */
    private String oauthTokenSecret;
    /**
     * 所属组织
     */
    private String organizationId;
    /**
     * 所属部门
     */
    private String departmentId;

}
