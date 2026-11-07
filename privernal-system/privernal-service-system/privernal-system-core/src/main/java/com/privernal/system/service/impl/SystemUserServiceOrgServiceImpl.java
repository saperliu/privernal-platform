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

package com.privernal.system.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.domain.SystemUserServiceOrg;
import com.privernal.system.filter.SystemUserServiceOrgFilterDTO;
import com.privernal.system.mapper.SystemUserServiceOrgMapper;
import com.privernal.system.service.ISystemUserServiceOrgService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务组织的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户服务组织的Service业务层处理
 */
@Slf4j
@Service
public class SystemUserServiceOrgServiceImpl implements ISystemUserServiceOrgService {
    @Autowired
    private SystemUserServiceOrgMapper systemUserServiceOrgMapper;

    /**
     * 查询用户服务组织
     *
     * @param id 用户服务组织主键
     * @return 用户服务组织
     */
    @Transactional(readOnly = true)
    @Override
    public SystemUserServiceOrg selectSystemUserServiceOrgById(String id) {
        return systemUserServiceOrgMapper.selectById(id);
    }

    /**
     * 查询用户服务组织列表
     *
     * @param systemUserServiceOrg 用户服务组织
     * @return 用户服务组织
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserServiceOrg> selectSystemUserServiceOrgList(SystemUserServiceOrgFilterDTO systemUserServiceOrg) {
        return systemUserServiceOrgMapper.selectSystemUserServiceOrgList(systemUserServiceOrg);
    }

    /**
     * 新增用户服务组织
     *
     * @param systemUserServiceOrg 用户服务组织
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemUserServiceOrg(SystemUserServiceOrg systemUserServiceOrg) {
        return systemUserServiceOrgMapper.insert(systemUserServiceOrg);
    }

    /**
     * 修改用户服务组织
     *
     * @param systemUserServiceOrg 用户服务组织
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemUserServiceOrg(SystemUserServiceOrg systemUserServiceOrg) {
        return systemUserServiceOrgMapper.updateById(systemUserServiceOrg);
    }

    /**
     * 批量删除用户服务组织
     *
     * @param ids 需要删除的用户服务组织主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserServiceOrgByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemUserServiceOrgById(id);
        }
        return ids.length;
    }

    /**
     * 删除用户服务组织信息
     *
     * @param id 用户服务组织主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserServiceOrgById(String id) {
        return systemUserServiceOrgMapper.deleteFillById(id);
    }

}
