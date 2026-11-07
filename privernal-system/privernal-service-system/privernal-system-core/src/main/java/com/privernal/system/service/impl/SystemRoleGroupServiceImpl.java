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
import com.privernal.system.domain.SystemRoleGroup;
import com.privernal.system.filter.SystemRoleGroupFilterDTO;
import com.privernal.system.mapper.SystemRoleGroupMapper;
import com.privernal.system.service.ISystemRoleGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色组的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色组的Service业务层处理
 */
@Slf4j
@Service
public class SystemRoleGroupServiceImpl implements ISystemRoleGroupService {
    @Autowired
    private SystemRoleGroupMapper systemRoleGroupMapper;

    /**
     * 查询角色组
     *
     * @param id 角色组主键
     * @return 角色组
     */
    @Transactional(readOnly = true)
    @Override
    public SystemRoleGroup selectSystemRoleGroupById(String id) {
        return systemRoleGroupMapper.selectById(id);
    }

    /**
     * 查询角色组列表
     *
     * @param systemRoleGroup 角色组
     * @return 角色组
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleGroup> selectSystemRoleGroupList(SystemRoleGroupFilterDTO systemRoleGroup) {
        return systemRoleGroupMapper.selectSystemRoleGroupList(systemRoleGroup);
    }

    /**
     * 新增角色组
     *
     * @param systemRoleGroup 角色组
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemRoleGroup(SystemRoleGroup systemRoleGroup) {
        return systemRoleGroupMapper.insert(systemRoleGroup);
    }

    /**
     * 修改角色组
     *
     * @param systemRoleGroup 角色组
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemRoleGroup(SystemRoleGroup systemRoleGroup) {
        return systemRoleGroupMapper.updateById(systemRoleGroup);
    }

    /**
     * 批量删除角色组
     *
     * @param ids 需要删除的角色组主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleGroupByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemRoleGroupById(id);
        }
        return ids.length;
    }

    /**
     * 删除角色组信息
     *
     * @param id 角色组主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleGroupById(String id) {
        return systemRoleGroupMapper.deleteFillById(id);
    }

}
