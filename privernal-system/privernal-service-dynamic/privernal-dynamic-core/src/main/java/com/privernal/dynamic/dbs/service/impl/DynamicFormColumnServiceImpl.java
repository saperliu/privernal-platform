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
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.mapper.DynamicFormColumnMapper;
import com.privernal.dynamic.dbs.service.IDynamicFormColumnService;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.privernal.dynamic.dto.DynamicFormColumnDetailDTO;
import com.privernal.dynamic.filter.DynamicFormColumnFilterDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.privernal.common.core.constant.DataConstants.DATA_DELETE_FLAG_NOT;

/**
 * form的列的数据的Service业务层处理
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description form的列的数据的Service业务层处理
 */
@Slf4j
@Service
public class DynamicFormColumnServiceImpl implements IDynamicFormColumnService {
    @Autowired
    private DynamicFormColumnMapper dynamicFormColumnMapper;

    /**
     * 查询form的列的数据
     *
     * @param id form的列的数据主键
     * @return form的列的数据
     */
    @Transactional(readOnly = true)
    @Override
    public DynamicFormColumn selectDynamicFormColumnById(String id) {
        return dynamicFormColumnMapper.selectById(id);
    }

    /**
     * 查询form的列的数据
     *
     * @param id form的列的数据主键
     * @return form的列的数据
     */
    @Transactional(readOnly = true)
    @Override
    public DynamicFormColumnDetailDTO selectDynamicFormColumnDetailDTOById(String id) {
        DynamicFormColumn dynamicFormColumn = dynamicFormColumnMapper.selectById(id);
        DynamicFormColumnDetailDTO dynamicFormColumnDetailDTO = new DynamicFormColumnDetailDTO();
        if (dynamicFormColumn != null) {
            BeanUtils.copyProperties(dynamicFormColumn, dynamicFormColumnDetailDTO, DataConstants.IGNORED_ON_COPY);
        }
        return dynamicFormColumnDetailDTO;
    }

    /**
     * 查询form的列的数据列表
     *
     * @param dynamicFormColumn form的列的数据
     * @return form的列的数据
     */

    @Transactional(readOnly = true)
    @Override
    public List<DynamicFormColumnDTO> selectDynamicFormColumnList(DynamicFormColumnFilterDTO dynamicFormColumn) {
        return dynamicFormColumnMapper.selectDynamicFormColumnList(dynamicFormColumn);
    }

    /**
     * 新增form的列的数据
     *
     * @param dynamicFormColumn form的列的数据
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicFormColumn insertDynamicFormColumn(DynamicFormColumn dynamicFormColumn) {
        dynamicFormColumnMapper.insert(dynamicFormColumn);
        return dynamicFormColumn;
    }

    /**
     * 修改form的列的数据
     *
     * @param dynamicFormColumn form的列的数据
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicFormColumn updateDynamicFormColumn(DynamicFormColumn dynamicFormColumn) {
        // 系统级公共字段（id/tenant_id/department_id 等）：定义与保存数据的默认值锁定不可修改，
        // 查询条件的默认值/列表显示/查询显示/排序可改。按库里的原值覆盖受保护属性，防前端绕过。
        if (dynamicFormColumn != null && DynamicFormColumn.isSystemField(dynamicFormColumn.getFiledCode())) {
            DynamicFormColumn stored = dynamicFormColumnMapper.selectById(dynamicFormColumn.getId());
            if (stored != null) {
                dynamicFormColumn.setFiledCode(stored.getFiledCode());
                dynamicFormColumn.setFiledName(stored.getFiledName());
                dynamicFormColumn.setFiledType(stored.getFiledType());
                dynamicFormColumn.setLength(stored.getLength());
                dynamicFormColumn.setScale(stored.getScale());
                dynamicFormColumn.setDefaultValue(stored.getDefaultValue());
            }
        }
        dynamicFormColumnMapper.updateById(dynamicFormColumn);
        return dynamicFormColumn;
    }

    /**
     * 批量删除form的列的数据
     *
     * @param ids 需要删除的form的列的数据主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteDynamicFormColumnByIds(String[] ids) {
        for (String id : ids) {
            deleteDynamicFormColumnById(id);
        }
        return ids.length;
    }

    /**
     * 删除form的列的数据信息
     *
     * @param id form的列的数据主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteDynamicFormColumnById(String id) {
        return dynamicFormColumnMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public DynamicFormColumn selectDynamicFormColumnByFormId(String formId, String fieldName) {
        LambdaPrivernalWrapper<DynamicFormColumn> queryWrapper = new LambdaPrivernalWrapper<>();
        queryWrapper.eq(DynamicFormColumn::getFormId, formId);
        queryWrapper.eq(DynamicFormColumn::getFiledCode, fieldName);
        queryWrapper.eq(DynamicFormColumn::getDeletedFlag, DATA_DELETE_FLAG_NOT);
        return dynamicFormColumnMapper.selectOne(queryWrapper);
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public void deleteDynamicFormColumnByFormId(String formId, String fieldName) {
        dynamicFormColumnMapper.deleteDynamicFormColumnByFormId(formId, fieldName);
    }

    @Transactional(readOnly = true)
    @Override
    public List<DynamicFormColumn> selectDynamicFormColumnByFormId(String formId) {
        LambdaPrivernalWrapper<DynamicFormColumn> queryWrapper = new LambdaPrivernalWrapper<>();
        queryWrapper.eq(DynamicFormColumn::getFormId, formId);
        queryWrapper.eq(DynamicFormColumn::getDeletedFlag, DATA_DELETE_FLAG_NOT);
        return dynamicFormColumnMapper.selectList(queryWrapper);
    }

    @Transactional(readOnly = true)
    @Override
    public List<DynamicFormColumnDTO> selectTableColumnByCode(String tableCode) {
        return dynamicFormColumnMapper.selectDynamicFormColumnByTableCode(tableCode);
    }

}
