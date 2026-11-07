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

package com.privernal.system.service;

import com.privernal.system.domain.SystemDictionaryData;
import com.privernal.system.filter.SystemDictionaryDataFilterDTO;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

/**
 * 字典数据的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 字典数据的Service接口
 */
public interface ISystemDictionaryDataService {
    /**
     * 查询字典数据
     *
     * @param id 字典数据主键
     * @return 字典数据
     */
    public SystemDictionaryData selectSystemDictionaryDataById(String id);

    /**
     * 查询字典数据列表
     *
     * @param systemDictionaryData 字典数据
     * @return 字典数据集合
     */
    public List<SystemDictionaryData> selectSystemDictionaryDataList(SystemDictionaryDataFilterDTO systemDictionaryData);

    /**
     * 根据字典类型和字典值查询字典数据
     *
     * @param dictType  字典类型
     * @param dictValue 字典值
     * @return 字典数据
     */
    public SystemDictionaryData findSystemDictionaryDataByTypeAndValue(String dictType, String dictValue);

    /**
     * 根据字典类型和字典值查询字典数据
     *
     * @param dictType  字典类型
     * @param dictValue 字典值
     * @return 字典数据
     */
    public JSONObject findSelectSystemDictionaryJSONDataByTypeAndValue(String dictType, String dictValue);

    /**
     * 新增字典数据
     *
     * @param systemDictionaryData 字典数据
     * @return 结果
     */
    public int insertSystemDictionaryData(SystemDictionaryData systemDictionaryData);

    /**
     * 修改字典数据
     *
     * @param systemDictionaryData 字典数据
     * @return 结果
     */
    public int updateSystemDictionaryData(SystemDictionaryData systemDictionaryData);

    /**
     * 批量删除字典数据
     *
     * @param ids 需要删除的字典数据主键集合
     * @return 结果
     */
    public int deleteSystemDictionaryDataByIds(String[] ids);

    /**
     * 删除字典数据信息
     *
     * @param id 字典数据主键
     * @return 结果
     */
    public int deleteSystemDictionaryDataById(String id);

}
