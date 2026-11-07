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
import com.privernal.system.domain.SystemUserGroup;
import com.privernal.system.dto.SystemUserGroupUserDTO;
import com.privernal.system.filter.SystemUserGroupFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户组Mapper接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户组Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:userGroup")
public interface SystemUserGroupMapper extends PrivernalMapper<SystemUserGroup> {
    /**
     * 查询用户组列表
     *
     * @param systemUserGroup 用户组
     * @return 用户组集合
     */
    @PrivernalDataScope(mainAlias = "userGroup", permission = "system:userGroup:list")
    public List<SystemUserGroup> selectSystemUserGroupList(SystemUserGroupFilterDTO systemUserGroup);

    /**
     * 查询用户组里的用户
     *
     * @param groupId 用户组ID
     * @return 用户集合
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemUserGroupUserDTO> selectSystemUserByGroupList(@Param("groupId") String groupId, @Param("nickName") String nickName);

    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemUserGroupUserDTO> selectGroupUserByDepartmentIdAndGroupCode(@Param("departmentId") String departmentId, @Param("groupCode") String groupCode);
}
