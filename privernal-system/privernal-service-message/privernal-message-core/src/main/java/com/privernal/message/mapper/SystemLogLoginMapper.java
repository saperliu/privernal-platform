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
import com.privernal.message.domain.SystemLogLogin;
import com.privernal.message.dto.SystemLogLoginDTO;
import com.privernal.message.filter.SystemLogLoginFilterDTO;

import java.util.List;

/**
 * 登录日志Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 登录日志Mapper接口
 */
@PrivernalDataScope(permissionMain = "message:systemLogLogin")
public interface SystemLogLoginMapper extends PrivernalMapper<SystemLogLogin> {
    /**
     * 查询登录日志列表
     *
     * @param systemLogLogin 登录日志
     * @return 登录日志集合
     */
    @PrivernalDataScope(mainAlias = "systemLogLogin", permission = "message:systemLogLogin:list")
    public List<SystemLogLoginDTO> selectSystemLogLoginList(SystemLogLoginFilterDTO systemLogLogin);

}
