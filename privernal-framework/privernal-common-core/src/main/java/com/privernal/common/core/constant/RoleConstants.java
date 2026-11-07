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

package com.privernal.common.core.constant;

/**
 * 角色与权限常量：内置超级角色标识、全权限通配、数据范围与用户组类型。
 *
 * @author saperliu
 */
public class RoleConstants
{
    private RoleConstants()
    {
    }

    /**
     * 平台内组织的超级管理员角色
     */
    public static final String SUPER_ROLE_ADMIN = "SuperRole";

    /**
     * 平台内系统超级管理员角色
     */
    public static final String SUPER_PLATFORM_ROLE_ADMIN = "SuperPlatformRole";

    /**
     * 所有接口的权限，菜单及按钮权限
     */
    public static final String SUPER_ALL_PERMISSION = "*:*:*";

    /**
     * 查询所有组织的角色时，判断修改的用户是否允许修改
     */
    public static final String ROLE_ALL_ORGANIZATION = "ALL_ORGANIZATION";

    /**
     * 公共组类型，USER_GROUP
     */
    public static final String GROUP_TYPE_USER_GROUP = "USER_GROUP";

    public static final String GROUP_TYPE_ROLE = "ROLE";
}
