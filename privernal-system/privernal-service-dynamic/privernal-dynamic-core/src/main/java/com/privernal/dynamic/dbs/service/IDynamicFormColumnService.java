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

package com.privernal.dynamic.dbs.service;

import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.privernal.dynamic.dto.DynamicFormColumnDetailDTO;
import com.privernal.dynamic.filter.DynamicFormColumnFilterDTO;

import java.util.List;

/**
 * form的列的数据的Service接口
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description form的列的数据的Service接口
 */
public interface IDynamicFormColumnService {
    /**
     * 查询form的列的数据
     *
     * @param id form的列的数据主键
     * @return form的列的数据
     */
    public DynamicFormColumn selectDynamicFormColumnById(String id);

    /**
     * 查询form的列的数据
     *
     * @param id form的列的数据主键
     * @return form的列的数据
     */
    public DynamicFormColumnDetailDTO selectDynamicFormColumnDetailDTOById(String id);

    /**
     * 查询form的列的数据列表
     *
     * @param dynamicFormColumn form的列的数据
     * @return form的列的数据集合
     */
    public List<DynamicFormColumnDTO> selectDynamicFormColumnList(DynamicFormColumnFilterDTO dynamicFormColumn);

    /**
     * 新增form的列的数据
     *
     * @param dynamicFormColumn form的列的数据
     * @return 结果
     */
    public DynamicFormColumn insertDynamicFormColumn(DynamicFormColumn dynamicFormColumn);

    /**
     * 修改form的列的数据
     *
     * @param dynamicFormColumn form的列的数据
     * @return 结果
     */
    public DynamicFormColumn updateDynamicFormColumn(DynamicFormColumn dynamicFormColumn);

    /**
     * 批量删除form的列的数据
     *
     * @param ids 需要删除的form的列的数据主键集合
     * @return 结果
     */
    public int deleteDynamicFormColumnByIds(String[] ids);

    /**
     * 删除form的列的数据信息
     *
     * @param id form的列的数据主键
     * @return 结果
     */
    public int deleteDynamicFormColumnById(String id);

    /**
     * 查询form的列的数据
     *
     * @param formId form的ID
     * @param fieldName form的ID
     * @return form的列的数据
     */
    public DynamicFormColumn selectDynamicFormColumnByFormId(String formId,String fieldName);

    /**
     * 删除form的列的数据
     *
     * @param formId form的ID
     * @param fieldName form的ID
     * @return form的列的数据
     */
    public void deleteDynamicFormColumnByFormId(String formId,String fieldName);

    /**
     * 查询form的列的数据
     *
     * @param formId form的ID
     * @return form的列的数据
     */
    public List<DynamicFormColumn> selectDynamicFormColumnByFormId(String formId);

    /**
     * 查询创建的表单通过编码
     *
     * @param tableCode 查询条件
     * @return 列表数据
     */
    public List<DynamicFormColumnDTO> selectTableColumnByCode(String tableCode);

}
