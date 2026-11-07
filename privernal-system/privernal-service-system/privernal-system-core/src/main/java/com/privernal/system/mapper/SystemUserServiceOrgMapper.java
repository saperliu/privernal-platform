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
import com.privernal.system.domain.SystemUserServiceOrg;
import com.privernal.system.filter.SystemUserServiceOrgFilterDTO;

import java.util.List;

/**
 * 用户服务组织Mapper接口
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户服务组织Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:userServiceOrg")
public interface SystemUserServiceOrgMapper extends PrivernalMapper<SystemUserServiceOrg> {
    /**
     * 查询用户服务组织列表
     *
     * @param systemUserServiceOrg 用户服务组织
     * @return 用户服务组织集合
     */
    @PrivernalDataScope(mainAlias = "userServiceOrg", permission = "system:userServiceOrg:list")
    public List<SystemUserServiceOrg> selectSystemUserServiceOrgList(SystemUserServiceOrgFilterDTO systemUserServiceOrg);

}
