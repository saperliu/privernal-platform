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

import com.privernal.system.domain.SystemUserServiceOrg;
import com.privernal.system.filter.SystemUserServiceOrgFilterDTO;

import java.util.List;

/**
 * 用户服务组织的Service接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户服务组织的Service接口
 */
public interface ISystemUserServiceOrgService {
    /**
     * 查询用户服务组织
     *
     * @param id 用户服务组织主键
     * @return 用户服务组织
     */
    public SystemUserServiceOrg selectSystemUserServiceOrgById(String id);

    /**
     * 查询用户服务组织列表
     *
     * @param systemUserServiceOrg 用户服务组织
     * @return 用户服务组织集合
     */
    public List<SystemUserServiceOrg> selectSystemUserServiceOrgList(SystemUserServiceOrgFilterDTO systemUserServiceOrg);

    /**
     * 新增用户服务组织
     *
     * @param systemUserServiceOrg 用户服务组织
     * @return 结果
     */
    public int insertSystemUserServiceOrg(SystemUserServiceOrg systemUserServiceOrg);

    /**
     * 修改用户服务组织
     *
     * @param systemUserServiceOrg 用户服务组织
     * @return 结果
     */
    public int updateSystemUserServiceOrg(SystemUserServiceOrg systemUserServiceOrg);

    /**
     * 批量删除用户服务组织
     *
     * @param ids 需要删除的用户服务组织主键集合
     * @return 结果
     */
    public int deleteSystemUserServiceOrgByIds(String[] ids);

    /**
     * 删除用户服务组织信息
     *
     * @param id 用户服务组织主键
     * @return 结果
     */
    public int deleteSystemUserServiceOrgById(String id);

}
