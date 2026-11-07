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
import com.privernal.system.domain.SystemUserGroupUser;
import com.privernal.system.filter.SystemUserGroupUserFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户组与用户Mapper接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户组与用户Mapper接口
 */
@IgnoreDataScope
public interface SystemUserGroupUserMapper extends PrivernalMapper<SystemUserGroupUser> {
    /**
     * 查询用户组与用户列表
     *
     * @param systemUserGroupUser 用户组与用户
     * @return 用户组与用户集合
     */
    @PrivernalDataScope(mainAlias = "userGroupUser", permission = "system:userGroupUser:list")
    public List<SystemUserGroupUser> selectSystemUserGroupUserList(SystemUserGroupUserFilterDTO systemUserGroupUser);

    /**
     * 删除用户时使用，清理所有用户组
     * <p>
     * 根据userId 查询系统用户组关系
     *
     * @param userId 应用的userId
     * @return
     */
    public List<SystemUserGroupUser> selectSystemUserGroupByUserId(@Param("userId") String userId);
}
