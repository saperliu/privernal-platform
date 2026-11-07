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

import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemOrganization;
import com.privernal.system.dto.SystemUserServiceOrgDTO;
import com.privernal.system.filter.SystemOrganizationFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 组织Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 组织Mapper接口
 */
@IgnoreDataScope
public interface SystemOrganizationMapper extends PrivernalMapper<SystemOrganization> {
    /**
     * 查询组织列表
     *
     * @param systemOrganization 组织
     * @return 组织集合
     */
    public List<SystemOrganization> selectSystemOrganizationList(SystemOrganizationFilterDTO systemOrganization);

    /**
     * 查询用户有的组织
     *
     * @param userId 用户ID
     * @return
     */
    public List<SystemUserServiceOrgDTO> selectUserSystemOrganizationList(String userId);

    /**
     * 查询所有组织
     *
     * @return
     */
    public List<SystemUserServiceOrgDTO> selectAllOrganizationList();

    /**
     * 通过组织编码查询组织
     *
     * @param code 编码
     * @return
     */
    public List<SystemOrganization> selectOrganizationByCode(@Param("code") String code);
}
