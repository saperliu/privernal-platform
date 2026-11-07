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

import com.privernal.system.domain.SystemUserGroup;
import com.privernal.system.dto.SystemUserGroupUserDTO;
import com.privernal.system.filter.SystemUserGroupFilterDTO;

import java.util.List;

/**
 * 用户组的Service接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户组的Service接口
 */
public interface ISystemUserGroupService {
    /**
     * 查询用户组
     *
     * @param id 用户组主键
     * @return 用户组
     */
    public SystemUserGroup selectSystemUserGroupById(String id);

    /**
     * 查询用户组列表
     *
     * @param systemUserGroup 用户组
     * @return 用户组集合
     */
    public List<SystemUserGroup> selectSystemUserGroupList(SystemUserGroupFilterDTO systemUserGroup);

    /**
     * 新增用户组
     *
     * @param systemUserGroup 用户组
     * @return 结果
     */
    public int insertSystemUserGroup(SystemUserGroup systemUserGroup);

    /**
     * 修改用户组
     *
     * @param systemUserGroup 用户组
     * @return 结果
     */
    public int updateSystemUserGroup(SystemUserGroup systemUserGroup);

    /**
     * 批量删除用户组
     *
     * @param ids 需要删除的用户组主键集合
     * @return 结果
     */
    public int deleteSystemUserGroupByIds(String[] ids);

    /**
     * 删除用户组信息
     *
     * @param id 用户组主键
     * @return 结果
     */
    public int deleteSystemUserGroupById(String id);

    /**
     * 查询用户组里的用户
     *
     * @param groupId  用户组ID
     * @param nickName 用户昵称
     * @return 用户集合
     */
    public List<SystemUserGroupUserDTO> selectSystemUserByGroupList(String groupId, String nickName);

    /**
     * @param departmentId
     * @param groupCode
     * @return
     */
    public List<SystemUserGroupUserDTO> selectGroupUserByDepartmentIdAndGroupCode(String departmentId, String groupCode);

    /**
     * 新增用户组里的用户
     *
     * @param systemUserGroupUserDTO 用户组里的用户关系
     * @return 结果
     */
    public int insertBatchSystemUserGroup(SystemUserGroupUserDTO systemUserGroupUserDTO);

}
