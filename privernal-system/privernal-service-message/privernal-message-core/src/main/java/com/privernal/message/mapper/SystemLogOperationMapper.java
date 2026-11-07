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

package com.privernal.message.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.message.domain.SystemLogOperation;
import com.privernal.message.dto.SystemLogOperationDTO;
import com.privernal.message.filter.SystemLogOperationFilterDTO;

import java.util.List;

/**
 * 操作日志Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 操作日志Mapper接口
 */
@PrivernalDataScope(permissionMain = "message:systemLogOperation")
public interface SystemLogOperationMapper extends PrivernalMapper<SystemLogOperation> {
    /**
     * 查询操作日志列表
     *
     * @param systemLogOperation 操作日志
     * @return 操作日志集合
     */
    @PrivernalDataScope(mainAlias = "systemLogOperation", permission = "message:systemLogOperation:list")
    public List<SystemLogOperationDTO> selectSystemLogOperationList(SystemLogOperationFilterDTO systemLogOperation);

}
