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

import com.privernal.system.domain.SystemRole;
import com.privernal.system.dto.SystemRoleDTO;
import com.privernal.system.dto.SystemRoleDataScopeDTO;
import com.privernal.system.dto.SystemRoleMenuDTO;
import com.privernal.system.dto.SystemRoleUserDTO;
import com.privernal.system.filter.SystemRoleFilterDTO;

import java.util.List;
import java.util.Set;

/**
 * 角色的Service接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色的Service接口
 */
public interface ISystemRoleService {
    /**
     * 查询角色
     *
     * @param id 角色主键
     * @return 角色
     */
    public SystemRole selectSystemRoleById(String id);

    /**
     * 查询角色列表
     *
     * @param systemRole 角色
     * @return 角色集合
     */
    public List<SystemRole> selectSystemRoleList(SystemRoleFilterDTO systemRole);

    /**
     * 查询角色列表
     *
     * @param systemRole 角色
     * @return 角色集合
     */
    public List<SystemRoleDTO> selectSystemRoleDTOList(SystemRoleFilterDTO systemRole);

    /**
     * 新增角色
     *
     * @param systemRole 角色
     * @return 结果
     */
    public int insertSystemRole(SystemRole systemRole);

    /**
     * 修改角色
     *
     * @param systemRole 角色
     * @return 结果
     */
    public int updateSystemRole(SystemRole systemRole);

    /**
     * 批量删除角色
     *
     * @param ids 需要删除的角色主键集合
     * @return 结果
     */
    public int deleteSystemRoleByIds(String[] ids);

    /**
     * 删除角色信息
     *
     * @param id 角色主键
     * @return 结果
     */
    public int deleteSystemRoleById(String id);

    /**
     * 获取角色数据权限
     *
     * @param userId         用户Id
     * @param organizationId 组织ID
     * @return 角色权限信息
     */
    public Set<String> getAuthRolePermission(String userId, String organizationId);

    /**
     * 获取菜单数据和按钮权限
     *
     * @param userId         用户Id
     * @param organizationId 组织ID
     * @param roles          角色KEY
     * @return 菜单权限信息
     */
    public Set<String> getAuthMenuPermission(String userId, String organizationId, Set<String> roles);

    /**
     * 查询用户此组织的角色权限对应的数据权限
     *
     * @param userId         用户ID
     * @param organizationId 组织ID
     * @param departmentId   部门的ID
     * @param roles          角色KEY
     * @return 数据
     */
    public List<SystemRoleDataScopeDTO> getAuthRoleDeptScope(String userId, String organizationId, String departmentId, Set<String> roles);

    /**
     * 查询角色里的用户
     *
     * @param roleId 角色ID
     * @return 用户集合
     */
    public List<SystemRoleUserDTO> selectSystemUserByRoleList(String roleId, String nickName);

    /**
     * 查询角色里的功能
     *
     * @param roleId 角色ID
     * @return 功能集合
     */
    public List<SystemRoleMenuDTO> selectSystemMenuByRoleList(String roleId);

    /**
     * 查询角色里的功能
     *
     * @param roleId 角色ID
     * @param appId  应用ID
     * @return 功能集合
     */
    public List<SystemRoleMenuDTO> selectSystemMenuByRoleList(String roleId, String appId);
}
