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

package com.privernal.system.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemDictionaryData;
import com.privernal.system.filter.SystemDictionaryDataFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 字典数据Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 字典数据Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:dictionaryData", ignoreDepartment = true)
public interface SystemDictionaryDataMapper extends PrivernalMapper<SystemDictionaryData> {
    /**
     * 查询字典数据列表
     *
     * @param systemDictionaryData 字典数据
     * @return 字典数据集合
     */
    @PrivernalDataScope(mainAlias = "dictionaryData", permission = "system:dictionaryData:list", ignoreDepartment = true)
    public List<SystemDictionaryData> selectSystemDictionaryDataList(SystemDictionaryDataFilterDTO systemDictionaryData);

    @PrivernalDataScope(mainAlias = "dictionaryData", permission = "system:dictionaryData:list", ignoreDepartment = true)
    public SystemDictionaryData selectSystemDictionaryDataByTypeAndValue(@Param("dictType") String dictType, @Param("dictValue") String dictValue);

}
