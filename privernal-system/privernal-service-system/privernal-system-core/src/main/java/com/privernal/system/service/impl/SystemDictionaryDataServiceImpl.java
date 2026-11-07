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
import com.privernal.system.domain.SystemDictionaryData;
import com.privernal.system.filter.SystemDictionaryDataFilterDTO;
import com.privernal.system.mapper.SystemDictionaryDataMapper;
import com.privernal.system.service.ISystemDictionaryDataService;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 字典数据的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 字典数据的Service业务层处理
 */
@Slf4j
@Service
public class SystemDictionaryDataServiceImpl implements ISystemDictionaryDataService {
    @Autowired
    private SystemDictionaryDataMapper systemDictionaryDataMapper;

    /**
     * 查询字典数据
     *
     * @param id 字典数据主键
     * @return 字典数据
     */
    @Transactional(readOnly = true)
    @Override
    public SystemDictionaryData selectSystemDictionaryDataById(String id) {
        return systemDictionaryDataMapper.selectById(id);
    }

    /**
     * 查询字典数据列表
     *
     * @param systemDictionaryData 字典数据
     * @return 字典数据
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemDictionaryData> selectSystemDictionaryDataList(SystemDictionaryDataFilterDTO systemDictionaryData) {
        return systemDictionaryDataMapper.selectSystemDictionaryDataList(systemDictionaryData);
    }

    /**
     * 根据字典类型和字典值查询字典数据
     *
     * @param dictType  字典类型
     * @param dictValue 字典值
     * @return 字典数据
     */
    @Transactional(readOnly = true)
    @Override
    public SystemDictionaryData findSystemDictionaryDataByTypeAndValue(String dictType, String dictValue) {
        return systemDictionaryDataMapper.selectSystemDictionaryDataByTypeAndValue(dictType, dictValue);
    }

    @Transactional(readOnly = true)
    @Override
    public JSONObject findSelectSystemDictionaryJSONDataByTypeAndValue(String dictType, String dictValue) {

        JSONObject result = null;
        SystemDictionaryData systemDictionaryData = systemDictionaryDataMapper.selectSystemDictionaryDataByTypeAndValue(dictType, dictValue);
        if (systemDictionaryData != null) {
            result = new JSONObject();
            result.put("selected", systemDictionaryData.getDictValue());
            List<SystemDictionaryData> sourceList = new ArrayList<>();
            sourceList.add(systemDictionaryData);
            result.put("sourceList", sourceList);
        }
        return result;
    }

    /**
     * 新增字典数据
     *
     * @param systemDictionaryData 字典数据
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemDictionaryData(SystemDictionaryData systemDictionaryData) {
        return systemDictionaryDataMapper.insert(systemDictionaryData);
    }

    /**
     * 修改字典数据
     *
     * @param systemDictionaryData 字典数据
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemDictionaryData(SystemDictionaryData systemDictionaryData) {
        return systemDictionaryDataMapper.updateById(systemDictionaryData);
    }

    /**
     * 批量删除字典数据
     *
     * @param ids 需要删除的字典数据主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemDictionaryDataByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemDictionaryDataById(id);
        }
        return ids.length;
    }

    /**
     * 删除字典数据信息
     *
     * @param id 字典数据主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemDictionaryDataById(String id) {
        return systemDictionaryDataMapper.deleteFillById(id);
    }

}
