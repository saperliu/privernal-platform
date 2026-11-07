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

import com.privernal.system.domain.SystemSocialUser;
import com.privernal.system.domain.SystemUser;
import com.privernal.system.domain.SystemUserSocialUser;
import com.privernal.system.dto.SystemSocialUserDTO;
import com.privernal.system.filter.SystemSocialUserFilterDTO;

import java.util.List;

/**
 * 社交账号的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 社交账号的Service接口
 */
public interface ISystemSocialUserService {
    /**
     * 查询社交账号
     *
     * @param id 社交账号主键
     * @return 社交账号
     */
    public SystemSocialUser selectSystemSocialUserById(String id);

    /**
     * 查询社交账号列表
     *
     * @param systemSocialUser 社交账号
     * @return 社交账号集合
     */
    public List<SystemSocialUser> selectSystemSocialUserList(SystemSocialUserFilterDTO systemSocialUser);

    /**
     * 新增社交账号
     *
     * @param systemSocialUser 社交账号
     * @return 结果
     */
    public int insertSystemSocialUser(SystemSocialUser systemSocialUser);

    /**
     * 修改社交账号
     *
     * @param systemSocialUser 社交账号
     * @return 结果
     */
    public int updateSystemSocialUser(SystemSocialUser systemSocialUser);

    /**
     * 批量删除社交账号
     *
     * @param ids 需要删除的社交账号主键集合
     * @return 结果
     */
    public int deleteSystemSocialUserByIds(String[] ids);

    /**
     * 删除社交账号信息
     *
     * @param id 社交账号主键
     * @return 结果
     */
    public int deleteSystemSocialUserById(String id);

    /**
     * 微信小程序授权登录保存用户信息
     *
     * @param sysSocialUser 小程序用户信息
     * @return 系统用户信息
     */
    public SystemUser findSocialUser(SystemSocialUserDTO sysSocialUser);

    /**
     * 第三方授权登录保存用户信息
     *
     * @param sysSocialUser 用户信息
     * @return 系统用户信息
     */
    public SystemUser saveSysSocialUser(SystemSocialUserDTO sysSocialUser);

    /**
     * 根据用户的ID，查询社交账号信息
     *
     * @param sysUserSocialUser 用户
     * @return
     */
    public SystemSocialUser findSysSocialUserByUser(SystemUserSocialUser sysUserSocialUser);
}
