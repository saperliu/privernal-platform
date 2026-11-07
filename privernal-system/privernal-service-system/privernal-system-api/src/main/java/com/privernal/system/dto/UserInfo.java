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

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 用户信息
 *
 * @author saperliu
 * @date 2022-04-13
 * @Description 第三方用户对象 system_social_user
 */
@Data
public class UserInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一标识
     */
    private String token;

    /**
     * 用户名id
     */
    private String userId;

    /**
     * 应用的授权id
     */
    private String openId;

    /**
     * 用户登录名
     */
    private String loginName;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * 登录IP地址
     */
    private String ipAddr;

    /**
     * 角色,角色KEY的集合，区分不同等级的角色。
     */
    private Set<String> roleKeys;

    /**
     * 权限列表,前端显示菜单和按钮的集合
     */
    private Set<String> permissions;

    /**
     * 用户类型（sys 系统用户）
     */
    private String userType;

    /**
     * 用户密码
     */
    private String password;

    /**
     * F4 挑战登录凭证：AES-GCM 封存的线摘要（可为空=未迁移，走 bcrypt 引导迁移）
     */
    private String passwordV2;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机
     */
    private String phone;

    /**
     * 性别;用户性别（0男 1女 2未知）
     */
    private String gender;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 状态;状态（0正常 1停用）
     */
    private Long status;

    /**
     * 身份证号;身份证号
     */
    private String identityNum;

    /**
     * 是否删除
     */
    private String deletedFlag;

    /**
     * 用户服务的组织
     */
    private List<SystemUserServiceOrgDTO> userServiceOrgDTOList;

    /**
     * 用户通过角色获取的数据权限
     */
    private List<SystemRoleDataScopeDTO> functionDeptList;

    /**
     * 所属组织ID【当前登录的组织】
     */
    private String organizationId;

    /**
     * 所属组织名称
     */
    private String organizationName;

    /**
     * 所属组织编码【登录时写入会话】
     * 动态表单建表的表名要带企业组织编码前缀，取它避免每次建表回查组织
     */
    private String organizationCode;

    /**
     * 所属部门ID【当前组织的默认部门】
     */
    private String departmentId;

    /**
     * 所属部门ID【当前组织的默认部门】
     */
    private String departmentName;

    /**
     * IOS的苹果授权的refreshToken，在注销用户时使用
     */
    private String iosRefreshToken;

    /**
     * 用户是否是初始密码
     */
    private boolean initPassword;

}
