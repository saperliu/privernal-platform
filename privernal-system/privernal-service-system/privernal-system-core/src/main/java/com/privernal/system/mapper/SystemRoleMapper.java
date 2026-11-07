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
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemRole;
import com.privernal.system.dto.SystemRoleDTO;
import com.privernal.system.dto.SystemRoleDataScopeDTO;
import com.privernal.system.dto.SystemRoleMenuDTO;
import com.privernal.system.dto.SystemRoleUserDTO;
import com.privernal.system.filter.SystemRoleFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色Mapper接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:role")
public interface SystemRoleMapper extends PrivernalMapper<SystemRole> {
    /**
     * 查询角色列表
     *
     * @param systemRole 角色
     * @return 角色集合
     */
    @PrivernalDataScope(mainAlias = "role", permission = "system:role:list")
    public List<SystemRole> selectSystemRoleList(SystemRoleFilterDTO systemRole);


    /**
     * 查询角色列表
     *
     * @param systemRole 角色
     * @return 角色集合
     */
    @PrivernalDataScope(mainAlias = "role", permission = "system:role:list", ignoreDepartment = true)
    public List<SystemRoleDTO> selectSystemRoleDTOList(SystemRoleFilterDTO systemRole);

    /**
     * 根据用户ID查询所有角色，跨组织，
     * 在登录查询角色时使用，用于系统平台管理员切换到其他组织时，判断是否是系统平台管理员
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemRole> selectAuthRolePermissionByUserId(@Param("userId") String userId);

    /**
     * 查询所有角色对应的数据权限
     *
     * @param userId         用户ID
     * @param organizationId 组织ID
     * @return 角色
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemRoleDataScopeDTO> selectRoleDeptScope(@Param("userId") String userId, @Param("organizationId") String organizationId);

    /**
     * 查询角色里的用户
     *
     * @param roleId 角色ID
     * @return 用户集合
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemRoleUserDTO> selectSystemUserByRoleList(@Param("roleId") String roleId, @Param("nickName") String nickName);

    /**
     * 查询角色里的功能
     *
     * @param roleId 角色ID
     * @return 用户集合
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemRoleMenuDTO> selectSystemMenuByRoleList(@Param("roleId") String roleId);

    /**
     * 查询角色里的功能
     *
     * @param roleId 角色ID
     * @param appId  角色ID
     * @return 用户集合
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemRoleMenuDTO> selectSystemMenuByRoleAndAppList(@Param("roleId") String roleId, @Param("appId") String appId);
}
