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

import com.privernal.system.domain.SystemOrganizationApp;
import com.privernal.system.dto.SystemOrganizationAppDTO;
import com.privernal.system.dto.SystemOrganizationAppDetailDTO;
import com.privernal.system.filter.SystemOrganizationAppFilterDTO;

import java.util.List;

/**
 * 组织授权的应用的Service接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 组织授权的应用的Service接口
 */
public interface ISystemOrganizationAppService {
    /**
     * 查询组织授权的应用
     *
     * @param id 组织授权的应用主键
     * @return 组织授权的应用
     */
    public SystemOrganizationApp selectSystemOrganizationAppById(String id);

    /**
     * 查询组织授权的应用
     *
     * @param id 组织授权的应用主键
     * @return 组织授权的应用
     */
    public SystemOrganizationAppDetailDTO selectSystemOrganizationAppDetailDTOById(String id);

    /**
     * 查询组织授权的应用列表
     *
     * @param systemOrganizationApp 组织授权的应用
     * @return 组织授权的应用集合
     */
    public List<SystemOrganizationAppDTO> selectSystemOrganizationAppList(SystemOrganizationAppFilterDTO systemOrganizationApp);

    /**
     * 新增组织授权的应用
     *
     * @param systemOrganizationApp 组织授权的应用
     * @return 结果
     */
    public SystemOrganizationApp insertSystemOrganizationApp(SystemOrganizationApp systemOrganizationApp);

    /**
     * 修改组织授权的应用
     *
     * @param systemOrganizationApp 组织授权的应用
     * @return 结果
     */
    public SystemOrganizationApp updateSystemOrganizationApp(SystemOrganizationApp systemOrganizationApp);

    /**
     * 批量删除组织授权的应用
     *
     * @param ids 需要删除的组织授权的应用主键集合
     * @return 结果
     */
    public int deleteSystemOrganizationAppByIds(String[] ids);

    /**
     * 删除组织授权的应用信息
     *
     * @param id 组织授权的应用主键
     * @return 结果
     */
    public int deleteSystemOrganizationAppById(String id);

}
