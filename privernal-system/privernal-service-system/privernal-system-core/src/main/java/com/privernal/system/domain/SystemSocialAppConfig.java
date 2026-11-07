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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 第三方应用配置(小程序)对象 system_social_app_config
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 第三方应用配置(小程序)的实体
 */
@Data
public class SystemSocialAppConfig extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;


    /**
     * 创建应用的分类
     * 如：钉钉，微信，github
     */
    private String source;

    /**
     * appId
     */
    private String appId;
    /**
     * 密钥
     */
    private String secret;

    /**
     * 应用的编码,区分哪个应用登录
     */
    private String appCode;

    /**
     * 创建应用的agentId
     */
    private String agentId;

    /**
     * 创建应用的scheme
     */
    private String scheme;

    /**
     * 创建应用的企业ID
     */
    private String corpId;

    /**
     * 应用的名称，解绑时区分解绑哪一个
     */
    private String appName;
    /**
     * 回调的url
     */
    private String appUrl;
    /**
     * 角色
     */
    private String roleId;
    /**
     * 是否需要审核用户
     */
    private String examineUser;
    /**
     * 所属组织
     */
    private String organizationId;
    /**
     * 部门
     */
    private String departmentId;

    /**
     * 是否同步员工
     * 1:同步
     * 0:不同步
     */
    private Integer synEmployee;

}
