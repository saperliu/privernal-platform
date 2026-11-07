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
import com.privernal.system.domain.SystemUserGroupUser;
import com.privernal.system.filter.SystemUserGroupUserFilterDTO;
import com.privernal.system.mapper.SystemUserGroupUserMapper;
import com.privernal.system.service.ISystemUserGroupUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户组与用户的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户组与用户的Service业务层处理
 */
@Slf4j
@Service
public class SystemUserGroupUserServiceImpl implements ISystemUserGroupUserService {
    @Autowired
    private SystemUserGroupUserMapper systemUserGroupUserMapper;

    /**
     * 查询用户组与用户
     *
     * @param id 用户组与用户主键
     * @return 用户组与用户
     */
    @Transactional(readOnly = true)
    @Override
    public SystemUserGroupUser selectSystemUserGroupUserById(String id) {
        return systemUserGroupUserMapper.selectById(id);
    }

    /**
     * 查询用户组与用户列表
     *
     * @param systemUserGroupUser 用户组与用户
     * @return 用户组与用户
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserGroupUser> selectSystemUserGroupUserList(SystemUserGroupUserFilterDTO systemUserGroupUser) {
        return systemUserGroupUserMapper.selectSystemUserGroupUserList(systemUserGroupUser);
    }

    /**
     * 新增用户组与用户
     *
     * @param systemUserGroupUser 用户组与用户
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemUserGroupUser(SystemUserGroupUser systemUserGroupUser) {
        return systemUserGroupUserMapper.insert(systemUserGroupUser);
    }

    /**
     * 修改用户组与用户
     *
     * @param systemUserGroupUser 用户组与用户
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemUserGroupUser(SystemUserGroupUser systemUserGroupUser) {
        return systemUserGroupUserMapper.updateById(systemUserGroupUser);
    }

    /**
     * 批量删除用户组与用户
     *
     * @param ids 需要删除的用户组与用户主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserGroupUserByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemUserGroupUserById(id);
        }
        return ids.length;
    }

    /**
     * 删除用户组与用户信息
     *
     * @param id 用户组与用户主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserGroupUserById(String id) {
        return systemUserGroupUserMapper.deleteFillById(id);
    }

}
