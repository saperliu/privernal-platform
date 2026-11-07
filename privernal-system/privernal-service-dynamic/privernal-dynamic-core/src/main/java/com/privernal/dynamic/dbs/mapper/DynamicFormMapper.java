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
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dto.DynamicFormDTO;
import com.privernal.dynamic.filter.DynamicFormFilterDTO;

/**
 * 前端form配置Mapper接口
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description 前端form配置Mapper接口
 */
@PrivernalDataScope(permissionMain = "dynamic:form")
public interface DynamicFormMapper extends PrivernalMapper<DynamicForm>
{
    /**
     * 查询前端form配置列表
     *
     * @param dynamicForm 前端form配置
     * @return 前端form配置集合
     */
    @PrivernalDataScope(mainAlias = "form",permission = "dynamic:form:list")
    public List<DynamicFormDTO> selectDynamicFormList(DynamicFormFilterDTO dynamicForm);

}
