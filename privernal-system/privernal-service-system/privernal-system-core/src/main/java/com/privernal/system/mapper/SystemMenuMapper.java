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

package com.privernal.system.mapper;

import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemMenu;
import com.privernal.system.dto.SystemMenuDTO;
import com.privernal.system.filter.SystemMenuFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 菜单Mapper接口
 */
@IgnoreDataScope
public interface SystemMenuMapper extends PrivernalMapper<SystemMenu> {
    /**
     * 查询菜单列表
     *
     * @param systemMenu 菜单
     * @return 菜单集合
     */
    public List<SystemMenu> selectSystemMenuList(SystemMenuFilterDTO systemMenu);

    /**
     * 根据用户ID查询权限，只查询菜单的权限标识
     * 未登录，查询权限时
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    public List<String> selectMenuPermsByUserId(@Param("userId") String userId, @Param("organizationId") String organizationId);

    /**
     * 根据用户ID查询功能权限,包含菜单下的按钮，
     * 授权角色/组织功能菜单时使用
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    public List<SystemMenu> selectMenuButtonTreeByUserId(@Param("userId") String userId, @Param("organizationId") String organizationId);


    /**
     * 路由专用
     * 根据用户ID查询菜单，不包含菜单下的按钮
     * 用户登录后，查询在该组织的菜单功能
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    public List<SystemMenu> selectRoutersTreeByUserId(@Param("userId") String userId, @Param("organizationId") String organizationId);

    /**
     * 根据组织ID查询菜单，
     * 给组织授权菜单时使用，查询被授权的组织的菜单，所以不能用当前人的组织ID
     *
     * @param organizationId 组织ID
     * @return 菜单列表
     */
    public List<SystemMenu> selectMenuAllByOrganizationId(@Param("organizationId") String organizationId);

    /**
     * 路由专用
     * 根据组织ID查询菜单
     * 组织管理员和超级管理员，直接查询所有菜单
     *
     * @param organizationId 组织ID
     * @return 菜单列表
     */
    public List<SystemMenu> selectRoutersByOrganizationId(@Param("organizationId") String organizationId);

    /**
     * 查询APP的功能菜单
     *
     * @param userId         用户
     * @param organizationId 组织
     * @return
     */
    public List<SystemMenu> getAuthAppMenuByUserId(@Param("userId") String userId, @Param("organizationId") String organizationId);


    /**
     * 根据用户ID查询功能权限,包含菜单下的按钮，
     * 授权角色的功能菜单
     *
     * @param userId         用户ID
     * @param organizationId 组织
     * @param appId          应用
     * @return 权限列表
     */
    public List<SystemMenuDTO> selectMenuButtonTreeByUserIdAndAppId(@Param("userId") String userId, @Param("organizationId") String organizationId, @Param("appId") String appId);


    /**
     * 根据用户ID查询功能权限,包含菜单下的按钮，
     * 授权角色的功能菜单
     *
     * @param organizationId 组织
     * @param appId          应用
     * @return 权限列表
     */
    public List<SystemMenuDTO> selectMenuButtonTreeByOrganizationAndAppId(@Param("organizationId") String organizationId, @Param("appId") String appId);


}
