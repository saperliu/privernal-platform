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
import com.privernal.system.domain.SystemUserSocialUser;
import com.privernal.system.filter.SystemUserSocialUserFilterDTO;
import com.privernal.system.mapper.SystemUserSocialUserMapper;
import com.privernal.system.service.ISystemUserSocialUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 社交帐号关联系统账号的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 社交帐号关联系统账号的Service业务层处理
 */
@Slf4j
@Service
public class SystemUserSocialUserServiceImpl implements ISystemUserSocialUserService {
    @Autowired
    private SystemUserSocialUserMapper systemUserSocialUserMapper;

    /**
     * 查询社交帐号关联系统账号
     *
     * @param id 社交帐号关联系统账号主键
     * @return 社交帐号关联系统账号
     */
    @Transactional(readOnly = true)
    @Override
    public SystemUserSocialUser selectSystemUserSocialUserById(String id) {
        return systemUserSocialUserMapper.selectById(id);
    }

    /**
     * 查询社交帐号关联系统账号列表
     *
     * @param systemUserSocialUser 社交帐号关联系统账号
     * @return 社交帐号关联系统账号
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserSocialUser> selectSystemUserSocialUserList(SystemUserSocialUserFilterDTO systemUserSocialUser) {
        return systemUserSocialUserMapper.selectSystemUserSocialUserList(systemUserSocialUser);
    }

    /**
     * 新增社交帐号关联系统账号
     *
     * @param systemUserSocialUser 社交帐号关联系统账号
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemUserSocialUser(SystemUserSocialUser systemUserSocialUser) {
        return systemUserSocialUserMapper.insert(systemUserSocialUser);
    }

    /**
     * 修改社交帐号关联系统账号
     *
     * @param systemUserSocialUser 社交帐号关联系统账号
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemUserSocialUser(SystemUserSocialUser systemUserSocialUser) {
        return systemUserSocialUserMapper.updateById(systemUserSocialUser);
    }

    /**
     * 批量删除社交帐号关联系统账号
     *
     * @param ids 需要删除的社交帐号关联系统账号主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserSocialUserByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemUserSocialUserById(id);
        }
        return ids.length;
    }

    /**
     * 删除社交帐号关联系统账号信息
     *
     * @param id 社交帐号关联系统账号主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserSocialUserById(String id) {
        return systemUserSocialUserMapper.deleteFillById(id);
    }

}
