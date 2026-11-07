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
import com.privernal.dynamic.dbs.domain.DynamicFormLog;
import com.privernal.dynamic.dbs.mapper.DynamicFormLogMapper;
import com.privernal.dynamic.dbs.service.IDynamicFormLogService;
import com.privernal.dynamic.dto.DynamicFormLogDTO;
import com.privernal.dynamic.dto.DynamicFormLogDetailDTO;
import com.privernal.dynamic.filter.DynamicFormLogFilterDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * form的历史版本的Service业务层处理
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description form的历史版本的Service业务层处理
 */
@Slf4j
@Service
public class DynamicFormLogServiceImpl implements IDynamicFormLogService {
    @Autowired
    private DynamicFormLogMapper dynamicFormLogMapper;

    /**
     * 查询form的历史版本
     *
     * @param id form的历史版本主键
     * @return form的历史版本
     */
    @Transactional(readOnly = true)
    @Override
    public DynamicFormLog selectDynamicFormLogById(String id) {
        return dynamicFormLogMapper.selectById(id);
    }

    /**
     * 查询form的历史版本
     *
     * @param id form的历史版本主键
     * @return form的历史版本
     */
    @Transactional(readOnly = true)
    @Override
    public DynamicFormLogDetailDTO selectDynamicFormLogDetailDTOById(String id) {
        DynamicFormLog dynamicFormLog = dynamicFormLogMapper.selectById(id);
        DynamicFormLogDetailDTO dynamicFormLogDetailDTO = new DynamicFormLogDetailDTO();
        if (dynamicFormLog != null) {
            BeanUtils.copyProperties(dynamicFormLog, dynamicFormLogDetailDTO, DataConstants.IGNORED_ON_COPY);
        }
        return dynamicFormLogDetailDTO;
    }

    /**
     * 查询form的历史版本列表
     *
     * @param dynamicFormLog form的历史版本
     * @return form的历史版本
     */

    @Transactional(readOnly = true)
    @Override
    public List<DynamicFormLogDTO> selectDynamicFormLogList(DynamicFormLogFilterDTO dynamicFormLog) {
        return dynamicFormLogMapper.selectDynamicFormLogList(dynamicFormLog);
    }

    /**
     * 新增form的历史版本
     *
     * @param dynamicFormLog form的历史版本
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicFormLog insertDynamicFormLog(DynamicFormLog dynamicFormLog) {
        dynamicFormLogMapper.insert(dynamicFormLog);
        return dynamicFormLog;
    }

    /**
     * 修改form的历史版本
     *
     * @param dynamicFormLog form的历史版本
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicFormLog updateDynamicFormLog(DynamicFormLog dynamicFormLog) {
        dynamicFormLogMapper.updateById(dynamicFormLog);
        return dynamicFormLog;
    }

    /**
     * 批量删除form的历史版本
     *
     * @param ids 需要删除的form的历史版本主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteDynamicFormLogByIds(String[] ids) {
        for (String id : ids) {
            deleteDynamicFormLogById(id);
        }
        return ids.length;
    }

    /**
     * 删除form的历史版本信息
     *
     * @param id form的历史版本主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteDynamicFormLogById(String id) {
        return dynamicFormLogMapper.deleteFillById(id);
    }

}
