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

import com.privernal.system.domain.SystemRoleGroupRole;
import com.privernal.system.filter.SystemRoleGroupRoleFilterDTO;

import java.util.List;

/**
 * 角色组与角色的Service接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色组与角色的Service接口
 */
public interface ISystemRoleGroupRoleService {
    /**
     * 查询角色组与角色
     *
     * @param id 角色组与角色主键
     * @return 角色组与角色
     */
    public SystemRoleGroupRole selectSystemRoleGroupRoleById(String id);

    /**
     * 查询角色组与角色列表
     *
     * @param systemRoleGroupRole 角色组与角色
     * @return 角色组与角色集合
     */
    public List<SystemRoleGroupRole> selectSystemRoleGroupRoleList(SystemRoleGroupRoleFilterDTO systemRoleGroupRole);

    /**
     * 新增角色组与角色
     *
     * @param systemRoleGroupRole 角色组与角色
     * @return 结果
     */
    public int insertSystemRoleGroupRole(SystemRoleGroupRole systemRoleGroupRole);

    /**
     * 修改角色组与角色
     *
     * @param systemRoleGroupRole 角色组与角色
     * @return 结果
     */
    public int updateSystemRoleGroupRole(SystemRoleGroupRole systemRoleGroupRole);

    /**
     * 批量删除角色组与角色
     *
     * @param ids 需要删除的角色组与角色主键集合
     * @return 结果
     */
    public int deleteSystemRoleGroupRoleByIds(String[] ids);

    /**
     * 删除角色组与角色信息
     *
     * @param id 角色组与角色主键
     * @return 结果
     */
    public int deleteSystemRoleGroupRoleById(String id);

}
