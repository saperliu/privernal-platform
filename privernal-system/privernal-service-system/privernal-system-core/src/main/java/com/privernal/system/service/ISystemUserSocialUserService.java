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

import com.privernal.system.domain.SystemUserSocialUser;
import com.privernal.system.filter.SystemUserSocialUserFilterDTO;

import java.util.List;

/**
 * 社交帐号关联系统账号的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 社交帐号关联系统账号的Service接口
 */
public interface ISystemUserSocialUserService {
    /**
     * 查询社交帐号关联系统账号
     *
     * @param id 社交帐号关联系统账号主键
     * @return 社交帐号关联系统账号
     */
    public SystemUserSocialUser selectSystemUserSocialUserById(String id);

    /**
     * 查询社交帐号关联系统账号列表
     *
     * @param systemUserSocialUser 社交帐号关联系统账号
     * @return 社交帐号关联系统账号集合
     */
    public List<SystemUserSocialUser> selectSystemUserSocialUserList(SystemUserSocialUserFilterDTO systemUserSocialUser);

    /**
     * 新增社交帐号关联系统账号
     *
     * @param systemUserSocialUser 社交帐号关联系统账号
     * @return 结果
     */
    public int insertSystemUserSocialUser(SystemUserSocialUser systemUserSocialUser);

    /**
     * 修改社交帐号关联系统账号
     *
     * @param systemUserSocialUser 社交帐号关联系统账号
     * @return 结果
     */
    public int updateSystemUserSocialUser(SystemUserSocialUser systemUserSocialUser);

    /**
     * 批量删除社交帐号关联系统账号
     *
     * @param ids 需要删除的社交帐号关联系统账号主键集合
     * @return 结果
     */
    public int deleteSystemUserSocialUserByIds(String[] ids);

    /**
     * 删除社交帐号关联系统账号信息
     *
     * @param id 社交帐号关联系统账号主键
     * @return 结果
     */
    public int deleteSystemUserSocialUserById(String id);

}
