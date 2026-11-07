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
import com.privernal.system.domain.SystemUserDepartment;
import com.privernal.system.dto.SystemUserDepartmentDTO;
import com.privernal.system.filter.SystemUserDepartmentFilterDTO;
import com.privernal.system.mapper.SystemUserDepartmentMapper;
import com.privernal.system.service.ISystemUserDepartmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户兼职部门的Service业务层处理
 *
 * @author saperliu
 * @date 2023-10-10
 * @Description 用户兼职部门的Service业务层处理
 */
@Slf4j
@Service
public class SystemUserDepartmentServiceImpl implements ISystemUserDepartmentService {
    @Autowired
    private SystemUserDepartmentMapper systemUserDepartmentMapper;

    /**
     * 查询用户兼职部门
     *
     * @param id 用户兼职部门主键
     * @return 用户兼职部门
     */
    @Transactional(readOnly = true)
    @Override
    public SystemUserDepartment selectSystemUserDepartmentById(String id) {
        return systemUserDepartmentMapper.selectById(id);
    }

    /**
     * 查询用户兼职部门列表
     *
     * @param systemUserDepartment 用户兼职部门
     * @return 用户兼职部门
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDepartmentDTO> selectSystemUserDepartmentList(SystemUserDepartmentFilterDTO systemUserDepartment) {
        return systemUserDepartmentMapper.selectSystemUserDepartmentList(systemUserDepartment);
    }

    /**
     * 新增用户兼职部门
     *
     * @param systemUserDepartment 用户兼职部门
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemUserDepartment(SystemUserDepartment systemUserDepartment) {
        return systemUserDepartmentMapper.insert(systemUserDepartment);
    }

    /**
     * 修改用户兼职部门
     *
     * @param systemUserDepartment 用户兼职部门
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemUserDepartment(SystemUserDepartment systemUserDepartment) {
        return systemUserDepartmentMapper.updateById(systemUserDepartment);
    }

    /**
     * 批量删除用户兼职部门
     *
     * @param ids 需要删除的用户兼职部门主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserDepartmentByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemUserDepartmentById(id);
        }
        return ids.length;
    }

    /**
     * 删除用户兼职部门信息
     *
     * @param id 用户兼职部门主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserDepartmentById(String id) {
        return systemUserDepartmentMapper.deleteFillById(id);
    }

}
