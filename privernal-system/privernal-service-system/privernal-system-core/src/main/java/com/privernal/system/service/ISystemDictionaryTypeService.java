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

import com.privernal.system.domain.SystemDictionaryType;
import com.privernal.system.filter.SystemDictionaryTypeFilterDTO;

import java.util.List;

/**
 * 字典类型的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 字典类型的Service接口
 */
public interface ISystemDictionaryTypeService {
    /**
     * 查询字典类型
     *
     * @param id 字典类型主键
     * @return 字典类型
     */
    public SystemDictionaryType selectSystemDictionaryTypeById(String id);

    /**
     * 查询字典类型列表
     *
     * @param systemDictionaryType 字典类型
     * @return 字典类型集合
     */
    public List<SystemDictionaryType> selectSystemDictionaryTypeList(SystemDictionaryTypeFilterDTO systemDictionaryType);

    /**
     * 新增字典类型
     *
     * @param systemDictionaryType 字典类型
     * @return 结果
     */
    public int insertSystemDictionaryType(SystemDictionaryType systemDictionaryType);

    /**
     * 修改字典类型
     *
     * @param systemDictionaryType 字典类型
     * @return 结果
     */
    public int updateSystemDictionaryType(SystemDictionaryType systemDictionaryType);

    /**
     * 批量删除字典类型
     *
     * @param ids 需要删除的字典类型主键集合
     * @return 结果
     */
    public int deleteSystemDictionaryTypeByIds(String[] ids);

    /**
     * 删除字典类型信息
     *
     * @param id 字典类型主键
     * @return 结果
     */
    public int deleteSystemDictionaryTypeById(String id);

    /**
     * 初始化域值
     *
     * @param organizationId 组织
     * @param departmentId   部门
     * @return
     */
    public int initDictionaryType(String organizationId, String departmentId);
}
