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

package com.privernal.system.service;

import com.privernal.system.domain.SystemMenu;
import com.privernal.system.dto.SystemMenuDTO;
import com.privernal.system.filter.SystemMenuFilterDTO;
import com.privernal.system.vo.RouterVo;

import java.util.List;
import java.util.Set;

/**
 * 菜单的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 菜单的Service接口
 */
public interface ISystemMenuService {
    /**
     * 查询菜单
     *
     * @param id 菜单主键
     * @return 菜单
     */
    public SystemMenu selectSystemMenuById(String id);

    /**
     * 查询菜单列表
     *
     * @param systemMenu 菜单
     * @return 菜单集合
     */
    public List<SystemMenu> selectSystemMenuList(SystemMenuFilterDTO systemMenu);

    /**
     * 新增菜单
     *
     * @param systemMenu 菜单
     * @return 结果
     */
    public int insertSystemMenu(SystemMenu systemMenu);

    /**
     * 修改菜单
     *
     * @param systemMenu 菜单
     * @return 结果
     */
    public int updateSystemMenu(SystemMenu systemMenu);

    /**
     * 批量删除菜单
     *
     * @param ids 需要删除的菜单主键集合
     * @return 结果
     */
    public int deleteSystemMenuByIds(String[] ids);

    /**
     * 删除菜单信息
     *
     * @param id 菜单主键
     * @return 结果
     */
    public int deleteSystemMenuById(String id);

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    public Set<String> getAuthMenuPermissionByUserId(String userId, String organizationId);

    /**
     * 根据用户ID查询菜单树信息,不包含按钮。
     * 只用于登录时获取左侧的菜单
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    public List<SystemMenu> getRoutersByUserId(String userId, String organizationId);

    /**
     * 根据用户ID查询菜单树,包含按钮
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    public List<SystemMenu> getMenuButtonTreeByUserId(String userId, String organizationId);

    /**
     * 根据用户ID查询菜单树,包含按钮
     * 新的授权，显示的数据
     *
     * @param userId         用户ID
     * @param organizationId 企业
     * @param appId          应用
     * @return 菜单列表
     */
    public List<SystemMenuDTO> getMenuButtonTreeByUserIdAndAppId(String userId, String organizationId, String appId);

    /**
     * 构建前端路由所需要的菜单
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    public List<RouterVo> buildMenus(List<SystemMenu> menus);

    /**
     * 根据用户ID查询APP的菜单
     * 废弃，APP的菜单由新的设计取代。system_app_info
     *
     * @param userId         用户ID
     * @param organizationId 组织ID
     * @return 菜单列表
     */
    public List<SystemMenu> getAuthAppMenuByUserId(String userId, String organizationId);

    /**
     * 根据用户ID查询菜单树信息,不包含按钮。
     * 只用于登录时获取左侧的菜单, 增加应用的ID，只查询某应用的菜单
     *
     * @param userId         用户ID
     * @param organizationId 组织ID
     * @param appId          应用ID
     * @param code           应用编码
     * @return 菜单列表
     */
    public List<SystemMenuDTO> getRoutersByUserIdAndAppId(String userId, String organizationId, String appId, String code);

    /**
     * 构建前端路由所需要的菜单
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    public List<RouterVo> buildMenuDTOs(List<SystemMenuDTO> menus);

    /**
     * 查询菜单
     *
     * @param code 菜单的编码
     * @return 菜单
     */
    public SystemMenu selectSystemMenuByCode(String code);
}
