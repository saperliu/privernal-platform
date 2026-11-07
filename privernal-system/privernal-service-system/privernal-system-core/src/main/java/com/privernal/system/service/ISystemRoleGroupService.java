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

import com.privernal.system.domain.SystemRoleGroup;
import com.privernal.system.filter.SystemRoleGroupFilterDTO;

import java.util.List;

/**
 * 角色组的Service接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色组的Service接口
 */
public interface ISystemRoleGroupService {
    /**
     * 查询角色组
     *
     * @param id 角色组主键
     * @return 角色组
     */
    public SystemRoleGroup selectSystemRoleGroupById(String id);

    /**
     * 查询角色组列表
     *
     * @param systemRoleGroup 角色组
     * @return 角色组集合
     */
    public List<SystemRoleGroup> selectSystemRoleGroupList(SystemRoleGroupFilterDTO systemRoleGroup);

    /**
     * 新增角色组
     *
     * @param systemRoleGroup 角色组
     * @return 结果
     */
    public int insertSystemRoleGroup(SystemRoleGroup systemRoleGroup);

    /**
     * 修改角色组
     *
     * @param systemRoleGroup 角色组
     * @return 结果
     */
    public int updateSystemRoleGroup(SystemRoleGroup systemRoleGroup);

    /**
     * 批量删除角色组
     *
     * @param ids 需要删除的角色组主键集合
     * @return 结果
     */
    public int deleteSystemRoleGroupByIds(String[] ids);

    /**
     * 删除角色组信息
     *
     * @param id 角色组主键
     * @return 结果
     */
    public int deleteSystemRoleGroupById(String id);

}
