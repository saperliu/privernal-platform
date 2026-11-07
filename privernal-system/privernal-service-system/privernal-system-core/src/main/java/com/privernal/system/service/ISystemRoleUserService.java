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

import com.privernal.system.domain.SystemRoleUser;
import com.privernal.system.dto.SystemRoleUserDTO;
import com.privernal.system.filter.SystemRoleUserFilterDTO;

import java.util.List;

/**
 * 角色与用户关系的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色与用户关系的Service接口
 */
public interface ISystemRoleUserService {
    /**
     * 查询角色与用户关系
     *
     * @param id 角色与用户关系主键
     * @return 角色与用户关系
     */
    public SystemRoleUser selectSystemRoleUserById(String id);

    /**
     * 查询角色与用户关系列表
     *
     * @param systemRoleUser 角色与用户关系
     * @return 角色与用户关系集合
     */
    public List<SystemRoleUser> selectSystemRoleUserList(SystemRoleUserFilterDTO systemRoleUser);

    /**
     * 新增角色与用户关系
     *
     * @param systemRoleUser 角色与用户关系
     * @return 结果
     */
    public int insertSystemRoleUser(SystemRoleUser systemRoleUser);

    /**
     * 新增角色与用户关系
     *
     * @param systemRoleUserDTO 角色与用户关系
     * @return 结果
     */
    public int insertBatchSystemRoleUser(SystemRoleUserDTO systemRoleUserDTO);

    /**
     * 修改角色与用户关系
     *
     * @param systemRoleUser 角色与用户关系
     * @return 结果
     */
    public int updateSystemRoleUser(SystemRoleUser systemRoleUser);

    /**
     * 批量删除角色与用户关系
     *
     * @param ids 需要删除的角色与用户关系主键集合
     * @return 结果
     */
    public int deleteSystemRoleUserByIds(String[] ids);

    /**
     * 删除角色与用户关系信息
     *
     * @param id 角色与用户关系主键
     * @return 结果
     */
    public int deleteSystemRoleUserById(String id);

    /**
     * 查询用户角色信息
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 角色与用户关系信息
     */
    public SystemRoleUserDTO selectRoleUserByUserIdAndRoleId(String userId, String roleId);

    /**
     * 获取角色数据权限
     *
     * @param userId         用户Id
     * @param organizationId 组织ID
     * @return 角色权限信息
     */
    public List<SystemRoleUserDTO> selectAuthRoleByUserId(String userId, String organizationId);

}
