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
import com.privernal.system.domain.SystemRoleGroup;
import com.privernal.system.filter.SystemRoleGroupFilterDTO;

import java.util.List;

/**
 * 角色组Mapper接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色组Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:roleGroup")
public interface SystemRoleGroupMapper extends PrivernalMapper<SystemRoleGroup> {
    /**
     * 查询角色组列表
     *
     * @param systemRoleGroup 角色组
     * @return 角色组集合
     */

    @PrivernalDataScope(mainAlias = "roleGroup", permission = "system:roleGroup:list")
    public List<SystemRoleGroup> selectSystemRoleGroupList(SystemRoleGroupFilterDTO systemRoleGroup);

}
