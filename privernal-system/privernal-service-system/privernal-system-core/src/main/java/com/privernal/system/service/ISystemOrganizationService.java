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

package com.privernal.system.service;

import com.privernal.system.domain.SystemOrganization;
import com.privernal.system.dto.SystemOrganizationAppCreateDTO;
import com.privernal.system.dto.SystemOrganizationCreateDTO;
import com.privernal.system.dto.SystemOrganizationDTO;
import com.privernal.system.dto.SystemOrganizationMenuCreateDTO;
import com.privernal.system.filter.SystemOrganizationFilterDTO;

import java.util.List;

/**
 * 组织的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 组织的Service接口
 */
public interface ISystemOrganizationService {
    /**
     * 查询组织
     *
     * @param id 组织主键
     * @return 组织
     */
    public SystemOrganization selectSystemOrganizationById(String id);

    /**
     * 查询组织列表
     *
     * @param systemOrganization 组织
     * @return 组织集合
     */
    public List<SystemOrganization> selectSystemOrganizationList(SystemOrganizationFilterDTO systemOrganization);

    /**
     * 新增组织
     *
     * @param systemOrganization 组织
     * @return 结果
     */
    public SystemOrganizationDTO insertSystemOrganization(SystemOrganizationCreateDTO systemOrganization);

    /**
     * 修改组织
     *
     * @param systemOrganization 组织
     * @return 结果
     */
    public int updateSystemOrganization(SystemOrganization systemOrganization);

    /**
     * 批量删除组织
     *
     * @param ids 需要删除的组织主键集合
     * @return 结果
     */
    public int deleteSystemOrganizationByIds(String[] ids);

    /**
     * 删除组织信息
     *
     * @param id 组织主键
     * @return 结果
     */
    public int deleteSystemOrganizationById(String id);

    /**
     * 授权组织菜单
     *
     * @param systemOrganizationMenuCreateDTO 组织
     * @return 结果
     */
    public int saveSystemOrganizationMenu(SystemOrganizationMenuCreateDTO systemOrganizationMenuCreateDTO);

    /**
     * 授权组织应用
     *
     * @param systemOrganizationAppCreateDTO
     * @return
     */
    public int saveSystemOrganizationApp(SystemOrganizationAppCreateDTO systemOrganizationAppCreateDTO);
}
