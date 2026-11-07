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

import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.system.domain.SystemDictionaryData;
import com.privernal.system.domain.SystemDictionaryType;
import com.privernal.system.filter.SystemDictionaryTypeFilterDTO;
import com.privernal.system.mapper.SystemDictionaryDataMapper;
import com.privernal.system.mapper.SystemDictionaryTypeMapper;
import com.privernal.system.service.ISystemDictionaryTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static com.privernal.common.core.constant.DataConstants.DATA_DELETE_FLAG_NOT;

/**
 * 字典类型的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 字典类型的Service业务层处理
 */
@Slf4j
@Service
public class SystemDictionaryTypeServiceImpl implements ISystemDictionaryTypeService {
    @Autowired
    private SystemDictionaryTypeMapper systemDictionaryTypeMapper;

    @Autowired
    private SystemDictionaryDataMapper systemDictionaryDataMapper;

    /**
     * 查询字典类型
     *
     * @param id 字典类型主键
     * @return 字典类型
     */
    @Transactional(readOnly = true)
    @Override
    public SystemDictionaryType selectSystemDictionaryTypeById(String id) {
        return systemDictionaryTypeMapper.selectById(id);
    }

    /**
     * 查询字典类型列表
     *
     * @param systemDictionaryType 字典类型
     * @return 字典类型
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemDictionaryType> selectSystemDictionaryTypeList(SystemDictionaryTypeFilterDTO systemDictionaryType) {
        return systemDictionaryTypeMapper.selectSystemDictionaryTypeList(systemDictionaryType);
    }

    /**
     * 新增字典类型
     *
     * @param systemDictionaryType 字典类型
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemDictionaryType(SystemDictionaryType systemDictionaryType) {
        return systemDictionaryTypeMapper.insert(systemDictionaryType);
    }

    /**
     * 修改字典类型
     *
     * @param systemDictionaryType 字典类型
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemDictionaryType(SystemDictionaryType systemDictionaryType) {
        return systemDictionaryTypeMapper.updateById(systemDictionaryType);
    }

    /**
     * 批量删除字典类型
     *
     * @param ids 需要删除的字典类型主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemDictionaryTypeByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemDictionaryTypeById(id);
        }
        return ids.length;
    }

    /**
     * 删除字典类型信息
     *
     * @param id 字典类型主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemDictionaryTypeById(String id) {
        return systemDictionaryTypeMapper.deleteFillById(id);
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int initDictionaryType(String organizationId, String departmentId) {

        List<SystemDictionaryType> dictList = systemDictionaryTypeMapper.selectList(SystemDictionaryType::getDeletedFlag, DATA_DELETE_FLAG_NOT);
        if (dictList != null && !dictList.isEmpty()) {

            String dept = departmentId;
            for (SystemDictionaryType dict : dictList) {

                boolean ignore = OrganizationContextHolder.isIgnore();
                long num = 1;
                try {

                    if (StringUtils.isEmpty(departmentId)) {
                        // 部门ID为空，则默认为组织ID，数据都为组织级，所以不会有部门不存在的问题
                        dept = organizationId;
                        // 查询指定组织的数据，没有则增加，如果有则不做任何操作
                        OrganizationContextHolder.setOrganizationId(organizationId);
                        num = systemDictionaryTypeMapper.selectCount(new LambdaPrivernalWrapper<SystemDictionaryType>().eq(SystemDictionaryType::getDictType, dict.getDictType()).eq(SystemDictionaryType::getDeletedFlag, DATA_DELETE_FLAG_NOT));
                    } else {
                        num = 0;
                    }
                } finally {
                    OrganizationContextHolder.setIgnore(ignore);
                    OrganizationContextHolder.clear();
                }
                if (num == 0) {
                    List<SystemDictionaryData> dataList = systemDictionaryDataMapper.selectList(new LambdaPrivernalWrapper<SystemDictionaryData>().eq(SystemDictionaryData::getDictType, dict.getDictType()).eq(SystemDictionaryData::getDeletedFlag, DATA_DELETE_FLAG_NOT));
                    if (dataList != null && !dataList.isEmpty()) {
                        for (SystemDictionaryData data : dataList) {
                            SystemDictionaryData data1 = new SystemDictionaryData();
                            BeanUtils.copyProperties(data, data1, DataConstants.IGNORED_ON_COPY);
                            data1.setOrganizationId(organizationId);
                            data1.setDepartmentId(dept);
                            data1.setId(null);
                            data1.setCreatedTime(new Date());
                            systemDictionaryDataMapper.insert(data1);
                        }
                    }
                    SystemDictionaryType systemDictionaryType = new SystemDictionaryType();
                    BeanUtils.copyProperties(dict, systemDictionaryType, DataConstants.IGNORED_ON_COPY);
                    systemDictionaryType.setOrganizationId(organizationId);
                    systemDictionaryType.setDepartmentId(dept);
                    systemDictionaryType.setId(null);
                    systemDictionaryType.setCreatedTime(new Date());
                    systemDictionaryTypeMapper.insert(systemDictionaryType);
                }
            }
        }
        return 1;
    }
}
