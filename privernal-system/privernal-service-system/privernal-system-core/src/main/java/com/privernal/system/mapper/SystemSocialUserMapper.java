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
import com.privernal.system.domain.SystemSocialUser;
import com.privernal.system.domain.SystemUser;
import com.privernal.system.domain.SystemUserSocialUser;
import com.privernal.system.dto.SystemSocialUserDTO;
import com.privernal.system.filter.SystemSocialUserFilterDTO;

import java.util.List;

/**
 * 社交账号Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 社交账号Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:socialUser")
public interface SystemSocialUserMapper extends PrivernalMapper<SystemSocialUser> {
    /**
     * 查询社交账号列表
     *
     * @param systemSocialUser 社交账号
     * @return 社交账号集合
     */
    @PrivernalDataScope(mainAlias = "socialUser", permission = "system:socialUser:list")
    public List<SystemSocialUser> selectSystemSocialUserList(SystemSocialUserFilterDTO systemSocialUser);


    /**
     * 根据社交账号登录
     *
     * @param systemSocialUserDTO 社交帐号信息
     * @return
     */
    @IgnoreDataScope
    public List<SystemUser> selectSysUserBySocial(SystemSocialUserDTO systemSocialUserDTO);

    /**
     * 根据用户的ID，查询社交账号信息
     *
     * @param sysUserSocialUser 用户
     * @return
     */
    @IgnoreDataScope
    public SystemSocialUser selectSysSocialUserByUser(SystemUserSocialUser sysUserSocialUser);
}
