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
import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemUserPosition;
import com.privernal.system.filter.SystemUserPositionFilterDTO;

import java.util.List;

/**
 * 用户的岗位Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 用户的岗位Mapper接口
 */
@IgnoreDataScope
public interface SystemUserPositionMapper extends PrivernalMapper<SystemUserPosition> {
    /**
     * 查询用户的岗位列表
     *
     * @param systemUserPosition 用户的岗位
     * @return 用户的岗位集合
     */
    @PrivernalDataScope(mainAlias = "position", permission = "system:position:list")
    public List<SystemUserPosition> selectSystemUserPositionList(SystemUserPositionFilterDTO systemUserPosition);

}
