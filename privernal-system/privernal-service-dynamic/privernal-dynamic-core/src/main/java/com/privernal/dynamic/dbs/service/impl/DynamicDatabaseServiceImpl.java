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

package com.privernal.dynamic.dbs.service.impl;

import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.dynamic.dbs.domain.DynamicDatabase;
import com.privernal.dynamic.dbs.mapper.DynamicDatabaseMapper;
import com.privernal.dynamic.dbs.service.IDynamicDatabaseService;
import com.privernal.dynamic.dto.DynamicDatabaseDTO;
import com.privernal.dynamic.dto.DynamicDatabaseDetailDTO;
import com.privernal.dynamic.filter.DynamicDatabaseFilterDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.privernal.common.core.constant.DataConstants.DATA_DELETE_FLAG_NOT;
import static com.privernal.common.core.constant.DataConstants.DATA_STATUS_YES;

/**
 * 动态数据库的Service业务层处理
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description 动态数据库的Service业务层处理
 */
@Slf4j
@Service
public class DynamicDatabaseServiceImpl implements IDynamicDatabaseService {
    @Autowired
    private DynamicDatabaseMapper dynamicDatabaseMapper;

    /**
     * 查询动态数据库
     *
     * @param id 动态数据库主键
     * @return 动态数据库
     */
    @Transactional(readOnly = true)
    @Override
    public DynamicDatabase selectDynamicDatabaseById(String id) {
        return dynamicDatabaseMapper.selectById(id);
    }

    /**
     * 查询动态数据库
     *
     * @param id 动态数据库主键
     * @return 动态数据库
     */
    @Transactional(readOnly = true)
    @Override
    public DynamicDatabaseDetailDTO selectDynamicDatabaseDetailDTOById(String id) {
        DynamicDatabase dynamicDatabase = dynamicDatabaseMapper.selectById(id);
        DynamicDatabaseDetailDTO dynamicDatabaseDetailDTO = new DynamicDatabaseDetailDTO();
        if (dynamicDatabase != null) {
            BeanUtils.copyProperties(dynamicDatabase, dynamicDatabaseDetailDTO, DataConstants.IGNORED_ON_COPY);
        }
        return dynamicDatabaseDetailDTO;
    }

    /**
     * 查询动态数据库列表
     *
     * @param dynamicDatabase 动态数据库
     * @return 动态数据库
     */

    @Transactional(readOnly = true)
    @Override
    public List<DynamicDatabaseDTO> selectDynamicDatabaseList(DynamicDatabaseFilterDTO dynamicDatabase) {
        return dynamicDatabaseMapper.selectDynamicDatabaseList(dynamicDatabase);
    }

    /**
     * 新增动态数据库
     *
     * @param dynamicDatabase 动态数据库
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicDatabase insertDynamicDatabase(DynamicDatabase dynamicDatabase) {
        dynamicDatabaseMapper.insert(dynamicDatabase);
        return dynamicDatabase;
    }

    /**
     * 修改动态数据库
     *
     * @param dynamicDatabase 动态数据库
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicDatabase updateDynamicDatabase(DynamicDatabase dynamicDatabase) {
        dynamicDatabaseMapper.updateById(dynamicDatabase);
        return dynamicDatabase;
    }

    /**
     * 批量删除动态数据库
     *
     * @param ids 需要删除的动态数据库主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteDynamicDatabaseByIds(String[] ids) {
        for (String id : ids) {
            deleteDynamicDatabaseById(id);
        }
        return ids.length;
    }

    /**
     * 删除动态数据库信息
     *
     * @param id 动态数据库主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteDynamicDatabaseById(String id) {
        return dynamicDatabaseMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public DynamicDatabaseDTO selectDynamicDatabase(String organizationId) {

        // 业务库按组织维度匹配（早期数据是按组织+部门登记的，部门维度不再作为条件）
        LambdaPrivernalWrapper<DynamicDatabase> queryWrapper = new LambdaPrivernalWrapper<>();
        queryWrapper.eq(DynamicDatabase::getOrganizationId, organizationId)
                .eq(DynamicDatabase::getDeletedFlag, DATA_DELETE_FLAG_NOT)
                .eq(DynamicDatabase::getStatus, DATA_STATUS_YES)
                .orderByAsc(DynamicDatabase::getCreatedTime);
        // 同一组织可能残留多条登记，selectOne 多行会抛 TooManyResultsException，取最早的一条
        List<DynamicDatabase> list = dynamicDatabaseMapper.selectList(queryWrapper);

        DynamicDatabaseDTO dynamicDatabaseDTO = new DynamicDatabaseDTO();
        if (!list.isEmpty()) {
            if (list.size() > 1) {
                log.warn(" 组织 {} 登记了 {} 个业务库，取最早登记的 {}", organizationId, list.size(), list.get(0).getDatabaseName());
            }
            BeanUtils.copyProperties(list.get(0), dynamicDatabaseDTO, DataConstants.IGNORED_ON_COPY);
        }

        return dynamicDatabaseDTO;
    }
}
