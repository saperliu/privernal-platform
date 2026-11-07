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
import com.privernal.system.domain.SystemRoleUser;
import com.privernal.system.dto.SystemRoleUserDTO;
import com.privernal.system.filter.SystemRoleUserFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色与用户关系Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 角色与用户关系Mapper接口
 */
@IgnoreDataScope
public interface SystemRoleUserMapper extends PrivernalMapper<SystemRoleUser> {
    /**
     * 查询角色与用户关系列表
     *
     * @param systemRoleUser 角色与用户关系
     * @return 角色与用户关系集合
     */
    @PrivernalDataScope(mainAlias = "roleUser", permission = "system:roleUser:list")
    public List<SystemRoleUser> selectSystemRoleUserList(SystemRoleUserFilterDTO systemRoleUser);

    /**
     * 查询用户拥有的角色
     *
     * @param userId 用户ID
     * @return 用户集合
     */
    public List<SystemRoleUser> selectSystemRoleUserByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID和角色ID查询角色与用户关系
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 角色与用户关系
     */

    public SystemRoleUserDTO selectRoleUserByUserIdAndRoleId(@Param("userId") String userId, @Param("roleId") String roleId);

    /**
     * 获取角色数据权限
     *
     * @param userId 用户Id
     * @return 角色权限信息
     */
    public List<SystemRoleUserDTO> selectAuthRoleByUserId(@Param("userId") String userId, @Param("organizationId") String organizationId);

}
