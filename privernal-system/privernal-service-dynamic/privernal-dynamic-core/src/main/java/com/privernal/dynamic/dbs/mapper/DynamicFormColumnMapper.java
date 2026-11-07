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

package com.privernal.dynamic.dbs.mapper;

import java.util.List;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.privernal.dynamic.filter.DynamicFormColumnFilterDTO;
import org.apache.ibatis.annotations.Param;


/**
 * form的列的数据Mapper接口
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description form的列的数据Mapper接口
 */
@PrivernalDataScope(permissionMain = "dynamic:formColumn")
public interface DynamicFormColumnMapper extends PrivernalMapper<DynamicFormColumn>
{
    /**
     * 查询form的列的数据列表
     *
     * @param dynamicFormColumn form的列的数据
     * @return form的列的数据集合
     */
    @PrivernalDataScope(mainAlias = "formColumn",permission = "dynamic:formColumn:list")
    public List<DynamicFormColumnDTO> selectDynamicFormColumnList(DynamicFormColumnFilterDTO dynamicFormColumn);

    /**
     * 删除form的列的数据
     *
     * @param formId form的ID
     * @param fieldName form的ID
     * @return form的列的数据
     */
    @PrivernalDataScope(mainAlias = "formColumn",permission = "dynamic:formColumn:remove")
    public void deleteDynamicFormColumnByFormId(@Param("formId")String formId,@Param("fieldName")String fieldName);

    /**
     * 删除form的列的数据
     *
     * @param tableCode 表的编码
     * @return form的列的数据
     */
    @PrivernalDataScope(mainAlias = "formColumn",permission = "dynamic:formColumn:list")
    public List<DynamicFormColumnDTO> selectDynamicFormColumnByTableCode(@Param("tableCode")String tableCode);
}
