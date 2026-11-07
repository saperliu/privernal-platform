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
import com.privernal.system.domain.SystemOrganizationApp;
import com.privernal.system.dto.SystemOrganizationAppDTO;
import com.privernal.system.filter.SystemOrganizationAppFilterDTO;

import java.util.List;

/**
 * 组织授权的应用Mapper接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 组织授权的应用Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:organizationApp")
public interface SystemOrganizationAppMapper extends PrivernalMapper<SystemOrganizationApp> {
    /**
     * 查询组织授权的应用列表
     *
     * @param systemOrganizationApp 组织授权的应用
     * @return 组织授权的应用集合
     */
    @PrivernalDataScope(mainAlias = "organizationApp", permission = "system:organizationApp:list")
    public List<SystemOrganizationAppDTO> selectSystemOrganizationAppList(SystemOrganizationAppFilterDTO systemOrganizationApp);

}
