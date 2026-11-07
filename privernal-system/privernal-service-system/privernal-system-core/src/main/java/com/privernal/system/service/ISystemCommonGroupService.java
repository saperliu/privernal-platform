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

import com.privernal.system.domain.SystemCommonGroup;
import com.privernal.system.dto.SystemCommonGroupDTO;
import com.privernal.system.dto.SystemCommonGroupDetailDTO;
import com.privernal.system.filter.SystemCommonGroupFilterDTO;

import java.util.List;

/**
 * 公共组[用户或角色]的Service接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 公共组[用户或角色]的Service接口
 */
public interface ISystemCommonGroupService {
    /**
     * 查询公共组[用户或角色]
     *
     * @param id 公共组[用户或角色]主键
     * @return 公共组[用户或角色]
     */
    public SystemCommonGroup selectSystemCommonGroupById(String id);

    /**
     * 查询公共组[用户或角色]
     *
     * @param id 公共组[用户或角色]主键
     * @return 公共组[用户或角色]
     */
    public SystemCommonGroupDetailDTO selectSystemCommonGroupDetailDTOById(String id);

    /**
     * 查询公共组[用户或角色]列表
     *
     * @param systemCommonGroup 公共组[用户或角色]
     * @return 公共组[用户或角色]集合
     */
    public List<SystemCommonGroupDTO> selectSystemCommonGroupList(SystemCommonGroupFilterDTO systemCommonGroup);

    /**
     * 新增公共组[用户或角色]
     *
     * @param systemCommonGroup 公共组[用户或角色]
     * @return 结果
     */
    public SystemCommonGroup insertSystemCommonGroup(SystemCommonGroup systemCommonGroup);

    /**
     * 修改公共组[用户或角色]
     *
     * @param systemCommonGroup 公共组[用户或角色]
     * @return 结果
     */
    public SystemCommonGroup updateSystemCommonGroup(SystemCommonGroup systemCommonGroup);

    /**
     * 新增或修改公共组信息
     *
     * @param systemCommonGroupDTO 公共组信息
     * @return 结果
     */
    public SystemCommonGroup saveSystemCommonGroupDTO(SystemCommonGroupDTO systemCommonGroupDTO);

    /**
     * 批量删除公共组[用户或角色]
     *
     * @param ids 需要删除的公共组[用户或角色]主键集合
     * @return 结果
     */
    public int deleteSystemCommonGroupByIds(String[] ids);

    /**
     * 删除公共组[用户或角色]信息
     *
     * @param id 公共组[用户或角色]主键
     * @return 结果
     */
    public int deleteSystemCommonGroupById(String id);

}
