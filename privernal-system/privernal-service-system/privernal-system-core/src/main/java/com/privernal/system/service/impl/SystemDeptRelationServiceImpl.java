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
import com.privernal.system.domain.SystemDeptRelation;
import com.privernal.system.filter.SystemDeptRelationFilterDTO;
import com.privernal.system.mapper.SystemDeptRelationMapper;
import com.privernal.system.service.ISystemDeptRelationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 部门内联关系的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 部门内联关系的Service业务层处理
 */
@Slf4j
@Service
public class SystemDeptRelationServiceImpl implements ISystemDeptRelationService {
    @Autowired
    private SystemDeptRelationMapper systemDeptRelationMapper;

    /**
     * 查询部门内联关系
     *
     * @param id 部门内联关系主键
     * @return 部门内联关系
     */
    @Transactional(readOnly = true)
    @Override
    public SystemDeptRelation selectSystemDeptRelationById(String id) {
        return systemDeptRelationMapper.selectById(id);
    }

    /**
     * 查询部门内联关系列表
     *
     * @param systemDeptRelation 部门内联关系
     * @return 部门内联关系
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemDeptRelation> selectSystemDeptRelationList(SystemDeptRelationFilterDTO systemDeptRelation) {
        return systemDeptRelationMapper.selectSystemDeptRelationList(systemDeptRelation);
    }

    /**
     * 新增部门内联关系
     *
     * @param systemDeptRelation 部门内联关系
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemDeptRelation(SystemDeptRelation systemDeptRelation) {
        return systemDeptRelationMapper.insert(systemDeptRelation);
    }

    /**
     * 修改部门内联关系
     *
     * @param systemDeptRelation 部门内联关系
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemDeptRelation(SystemDeptRelation systemDeptRelation) {
        return systemDeptRelationMapper.updateById(systemDeptRelation);
    }

    /**
     * 批量删除部门内联关系
     *
     * @param ids 需要删除的部门内联关系主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemDeptRelationByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemDeptRelationById(id);
        }
        return ids.length;
    }

    /**
     * 删除部门内联关系信息
     *
     * @param id 部门内联关系主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemDeptRelationById(String id) {
        return systemDeptRelationMapper.deleteFillById(id);
    }

}
