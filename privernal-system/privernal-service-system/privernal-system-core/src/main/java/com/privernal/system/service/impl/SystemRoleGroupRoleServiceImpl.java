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
import com.privernal.system.domain.SystemRoleGroupRole;
import com.privernal.system.filter.SystemRoleGroupRoleFilterDTO;
import com.privernal.system.mapper.SystemRoleGroupRoleMapper;
import com.privernal.system.service.ISystemRoleGroupRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色组与角色的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色组与角色的Service业务层处理
 */
@Slf4j
@Service
public class SystemRoleGroupRoleServiceImpl implements ISystemRoleGroupRoleService {
    @Autowired
    private SystemRoleGroupRoleMapper systemRoleGroupRoleMapper;

    /**
     * 查询角色组与角色
     *
     * @param id 角色组与角色主键
     * @return 角色组与角色
     */
    @Transactional(readOnly = true)
    @Override
    public SystemRoleGroupRole selectSystemRoleGroupRoleById(String id) {
        return systemRoleGroupRoleMapper.selectById(id);
    }

    /**
     * 查询角色组与角色列表
     *
     * @param systemRoleGroupRole 角色组与角色
     * @return 角色组与角色
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleGroupRole> selectSystemRoleGroupRoleList(SystemRoleGroupRoleFilterDTO systemRoleGroupRole) {
        return systemRoleGroupRoleMapper.selectSystemRoleGroupRoleList(systemRoleGroupRole);
    }

    /**
     * 新增角色组与角色
     *
     * @param systemRoleGroupRole 角色组与角色
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemRoleGroupRole(SystemRoleGroupRole systemRoleGroupRole) {
        return systemRoleGroupRoleMapper.insert(systemRoleGroupRole);
    }

    /**
     * 修改角色组与角色
     *
     * @param systemRoleGroupRole 角色组与角色
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemRoleGroupRole(SystemRoleGroupRole systemRoleGroupRole) {
        return systemRoleGroupRoleMapper.updateById(systemRoleGroupRole);
    }

    /**
     * 批量删除角色组与角色
     *
     * @param ids 需要删除的角色组与角色主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleGroupRoleByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemRoleGroupRoleById(id);
        }
        return ids.length;
    }

    /**
     * 删除角色组与角色信息
     *
     * @param id 角色组与角色主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleGroupRoleById(String id) {
        return systemRoleGroupRoleMapper.deleteFillById(id);
    }

}
