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

import com.privernal.system.domain.SystemOrganizationMenu;
import com.privernal.system.dto.SystemOrganizationMenuDTO;
import com.privernal.system.filter.SystemOrganizationMenuFilterDTO;

import java.util.List;

/**
 * 组织拥有的菜单的Service接口
 *
 * @author saperliu
 * @date 2023-04-17
 * @Description 组织拥有的菜单的Service接口
 */
public interface ISystemOrganizationMenuService {
    /**
     * 查询组织拥有的菜单
     *
     * @param id 组织拥有的菜单主键
     * @return 组织拥有的菜单
     */
    public SystemOrganizationMenu selectSystemOrganizationMenuById(String id);

    /**
     * 查询组织拥有的菜单列表
     *
     * @param systemOrganizationMenu 组织拥有的菜单
     * @return 组织拥有的菜单集合
     */
    public List<SystemOrganizationMenuDTO> selectSystemOrganizationMenuList(SystemOrganizationMenuFilterDTO systemOrganizationMenu);

    /**
     * 新增组织拥有的菜单
     *
     * @param systemOrganizationMenu 组织拥有的菜单
     * @return 结果
     */
    public int insertSystemOrganizationMenu(SystemOrganizationMenu systemOrganizationMenu);

    /**
     * 修改组织拥有的菜单
     *
     * @param systemOrganizationMenu 组织拥有的菜单
     * @return 结果
     */
    public int updateSystemOrganizationMenu(SystemOrganizationMenu systemOrganizationMenu);

    /**
     * 批量删除组织拥有的菜单
     *
     * @param ids 需要删除的组织拥有的菜单主键集合
     * @return 结果
     */
    public int deleteSystemOrganizationMenuByIds(String[] ids);

    /**
     * 删除组织拥有的菜单信息
     *
     * @param id 组织拥有的菜单主键
     * @return 结果
     */
    public int deleteSystemOrganizationMenuById(String id);

}
