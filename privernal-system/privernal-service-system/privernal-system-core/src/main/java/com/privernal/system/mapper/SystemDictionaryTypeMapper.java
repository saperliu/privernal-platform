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
import com.privernal.system.domain.SystemDictionaryType;
import com.privernal.system.filter.SystemDictionaryTypeFilterDTO;

import java.util.List;

/**
 * 字典类型Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 字典类型Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:dictionaryType", ignoreDepartment = true)
public interface SystemDictionaryTypeMapper extends PrivernalMapper<SystemDictionaryType> {
    /**
     * 查询字典类型列表
     *
     * @param systemDictionaryType 字典类型
     * @return 字典类型集合
     */
    @PrivernalDataScope(mainAlias = "dictionaryType", permission = "system:dictionaryType:list", ignoreDepartment = true)
    public List<SystemDictionaryType> selectSystemDictionaryTypeList(SystemDictionaryTypeFilterDTO systemDictionaryType);

}
