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
import com.privernal.system.domain.SystemRoleMenu;
import com.privernal.system.filter.SystemRoleMenuFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色与菜单关系Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 角色与菜单关系Mapper接口
 */
@IgnoreDataScope
public interface SystemRoleMenuMapper extends PrivernalMapper<SystemRoleMenu> {
    /**
     * 查询角色与菜单关系列表
     *
     * @param systemRoleMenu 角色与菜单关系
     * @return 角色与菜单关系集合
     */
    @PrivernalDataScope(mainAlias = "roleMenu", permission = "system:roleMenu:list")
    public List<SystemRoleMenu> selectSystemRoleMenuList(SystemRoleMenuFilterDTO systemRoleMenu);

    /**
     * 在组织授权功能时，删除已经授权的功能，同时删除角色与功能的关系。
     *
     * @param organizationId 组织
     * @param menuIds        功能
     * @return
     */
    public int deleteByOrganizationAndMenu(@Param("organizationId") String organizationId, @Param("appId") String appId, @Param("menuIds") List<String> menuIds);

    /**
     * 删除某功能菜单与所有角色的关联
     *
     * @param menuIds 菜单ID
     * @param appId   应用的ID
     * @return 删除记录数
     */
    public int deleteAllRoleMenuByMenuId(@Param("appId") String appId, @Param("menuIds") List<String> menuIds);


    /**
     * 删除某功能菜单与所有角色的关联
     *
     * @param menuIds 菜单ID
     * @param roleKey 角色的编码以
     * @return 删除记录数
     */
    public int deleteAllRoleMenuByRoleKeyAndMenuId(@Param("roleKey") String roleKey, @Param("menuIds") List<String> menuIds);
}
