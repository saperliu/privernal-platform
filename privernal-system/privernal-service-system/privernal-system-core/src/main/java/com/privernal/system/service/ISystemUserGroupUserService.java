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

import com.privernal.system.domain.SystemUserGroupUser;
import com.privernal.system.filter.SystemUserGroupUserFilterDTO;

import java.util.List;

/**
 * 用户组与用户的Service接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户组与用户的Service接口
 */
public interface ISystemUserGroupUserService {
    /**
     * 查询用户组与用户
     *
     * @param id 用户组与用户主键
     * @return 用户组与用户
     */
    public SystemUserGroupUser selectSystemUserGroupUserById(String id);

    /**
     * 查询用户组与用户列表
     *
     * @param systemUserGroupUser 用户组与用户
     * @return 用户组与用户集合
     */
    public List<SystemUserGroupUser> selectSystemUserGroupUserList(SystemUserGroupUserFilterDTO systemUserGroupUser);

    /**
     * 新增用户组与用户
     *
     * @param systemUserGroupUser 用户组与用户
     * @return 结果
     */
    public int insertSystemUserGroupUser(SystemUserGroupUser systemUserGroupUser);

    /**
     * 修改用户组与用户
     *
     * @param systemUserGroupUser 用户组与用户
     * @return 结果
     */
    public int updateSystemUserGroupUser(SystemUserGroupUser systemUserGroupUser);

    /**
     * 批量删除用户组与用户
     *
     * @param ids 需要删除的用户组与用户主键集合
     * @return 结果
     */
    public int deleteSystemUserGroupUserByIds(String[] ids);

    /**
     * 删除用户组与用户信息
     *
     * @param id 用户组与用户主键
     * @return 结果
     */
    public int deleteSystemUserGroupUserById(String id);

}
