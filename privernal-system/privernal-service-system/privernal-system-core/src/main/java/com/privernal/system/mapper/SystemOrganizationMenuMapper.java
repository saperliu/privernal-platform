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

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemOrganizationMenu;
import com.privernal.system.dto.SystemOrganizationMenuDTO;
import com.privernal.system.filter.SystemOrganizationMenuFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 组织拥有的菜单Mapper接口
 *
 * @author saperliu
 * @date 2023-04-17
 * @Description 组织拥有的菜单Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:organizationMenu")
public interface SystemOrganizationMenuMapper extends PrivernalMapper<SystemOrganizationMenu> {
    /**
     * 查询组织拥有的菜单列表
     *
     * @param systemOrganizationMenu 组织拥有的菜单
     * @return 组织拥有的菜单集合
     */
    @PrivernalDataScope(mainAlias = "organizationMenu", permission = "system:organizationMenu:list")
    public List<SystemOrganizationMenuDTO> selectSystemOrganizationMenuList(SystemOrganizationMenuFilterDTO systemOrganizationMenu);

    /**
     * 根据组织ID查询菜单，
     * 给组织授权菜单时使用，查询被授权的组织的菜单，所以不能用当前人的组织ID
     *
     * @param organizationId 组织ID
     * @return 菜单列表
     */
    @IgnoreDataScope
    public List<SystemOrganizationMenuDTO> selectMenuAllByOrganizationId(@Param("organizationId") String organizationId);

    /**
     * 根据组织ID和应用ID查询菜单，
     * 给组织授权菜单时使用，查询被授权的组织的菜单，所以不能用当前人的组织ID
     *
     * @param organizationId 组织ID
     * @return 菜单列表
     */
    @IgnoreDataScope
    public List<SystemOrganizationMenuDTO> selectSystemOrganizationMenuListByAppId(@Param("organizationId") String organizationId, @Param("appId") String appId);

    /**
     * 根据应用ID和菜单ID删除所有组织的此菜单的关联
     *
     * @param appId   应用ID
     * @param menuIds 菜单ID
     * @return
     */
    @IgnoreDataScope
    public int deleteAllOrganizationMenuByAppIdAndMenuId(@Param("appId") String appId, @Param("menuIds") List<String> menuIds);


}
