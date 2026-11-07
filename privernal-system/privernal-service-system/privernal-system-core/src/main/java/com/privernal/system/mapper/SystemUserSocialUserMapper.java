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
import com.privernal.system.domain.SystemUserSocialUser;
import com.privernal.system.filter.SystemUserSocialUserFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 社交帐号关联系统账号Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 社交帐号关联系统账号Mapper接口
 */
@IgnoreDataScope
public interface SystemUserSocialUserMapper extends PrivernalMapper<SystemUserSocialUser> {
    /**
     * 查询社交帐号关联系统账号列表
     *
     * @param systemUserSocialUser 社交帐号关联系统账号
     * @return 社交帐号关联系统账号集合
     */
    @PrivernalDataScope(mainAlias = "userSocialUser", permission = "system:userSocialUser:list")
    public List<SystemUserSocialUser> selectSystemUserSocialUserList(SystemUserSocialUserFilterDTO systemUserSocialUser);

    /**
     * 根据openId 查询系统用户ID
     * 社交账号登录时，查询是否注册使用
     *
     * @param openId 应用的openId
     * @return
     */
    @IgnoreDataScope
    public SystemUserSocialUser selectSysUserSocialUserByOpenId(String openId);

    /**
     * 钉钉同步用户时使用，判断是否已经同步过。
     * 社交账号登录时，查询是否注册使用
     *
     * @param unionId        应用的用户unionId
     * @param organizationId 组织ID
     * @return
     */
    @IgnoreDataScope
    public SystemUserSocialUser selectSysUserSocialUserByUnionId(@Param("unionId") String unionId, @Param("organizationId") String organizationId);

    /**
     * 根据userId 查询系统用户ID
     *
     * @param userId 应用的userId
     * @return
     */
    public SystemUserSocialUser selectSysUserSocialUserByUserId(@Param("userId") String userId);
}
