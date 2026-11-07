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

import com.privernal.system.domain.SystemRoleMenu;
import com.privernal.system.dto.SystemRoleMenuDTO;
import com.privernal.system.filter.SystemRoleMenuFilterDTO;

import java.util.List;

/**
 * 角色与菜单关系的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色与菜单关系的Service接口
 */
public interface ISystemRoleMenuService {
    /**
     * 查询角色与菜单关系
     *
     * @param id 角色与菜单关系主键
     * @return 角色与菜单关系
     */
    public SystemRoleMenu selectSystemRoleMenuById(String id);

    /**
     * 查询角色与菜单关系列表
     *
     * @param systemRoleMenu 角色与菜单关系
     * @return 角色与菜单关系集合
     */
    public List<SystemRoleMenu> selectSystemRoleMenuList(SystemRoleMenuFilterDTO systemRoleMenu);

    /**
     * 新增角色与菜单关系
     *
     * @param systemRoleMenu 角色与菜单关系
     * @return 结果
     */
    public int insertSystemRoleMenu(SystemRoleMenu systemRoleMenu);

    /**
     * 新增角色与菜单关系
     *
     * @param systemRoleMenuDTO 角色与菜单关系
     * @return 结果
     */
    public int insertBatchSystemRoleMenu(SystemRoleMenuDTO systemRoleMenuDTO);

    /**
     * 修改角色与菜单关系
     *
     * @param systemRoleMenu 角色与菜单关系
     * @return 结果
     */
    public int updateSystemRoleMenu(SystemRoleMenu systemRoleMenu);

    /**
     * 批量删除角色与菜单关系
     *
     * @param ids 需要删除的角色与菜单关系主键集合
     * @return 结果
     */
    public int deleteSystemRoleMenuByIds(String[] ids);

    /**
     * 删除角色与菜单关系信息
     *
     * @param id 角色与菜单关系主键
     * @return 结果
     */
    public int deleteSystemRoleMenuById(String id);

}
