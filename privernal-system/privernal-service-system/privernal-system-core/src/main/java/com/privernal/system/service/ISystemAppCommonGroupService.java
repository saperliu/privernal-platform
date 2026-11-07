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

import com.privernal.system.domain.SystemAppCommonGroup;
import com.privernal.system.dto.SystemAppCommonGroupDTO;
import com.privernal.system.dto.SystemAppCommonGroupDetailDTO;
import com.privernal.system.filter.SystemAppCommonGroupFilterDTO;

import java.util.List;

/**
 * 应用关联的公共组的Service接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用关联的公共组的Service接口
 */
public interface ISystemAppCommonGroupService {
    /**
     * 查询应用关联的公共组
     *
     * @param id 应用关联的公共组主键
     * @return 应用关联的公共组
     */
    public SystemAppCommonGroup selectSystemAppCommonGroupById(String id);

    /**
     * 查询应用关联的公共组
     *
     * @param id 应用关联的公共组主键
     * @return 应用关联的公共组
     */
    public SystemAppCommonGroupDetailDTO selectSystemAppCommonGroupDetailDTOById(String id);

    /**
     * 查询应用关联的公共组列表
     *
     * @param systemAppCommonGroup 应用关联的公共组
     * @return 应用关联的公共组集合
     */
    public List<SystemAppCommonGroupDTO> selectSystemAppCommonGroupList(SystemAppCommonGroupFilterDTO systemAppCommonGroup);

    /**
     * 新增应用关联的公共组
     *
     * @param systemAppCommonGroup 应用关联的公共组
     * @return 结果
     */
    public SystemAppCommonGroup insertSystemAppCommonGroup(SystemAppCommonGroup systemAppCommonGroup);

    /**
     * 修改应用关联的公共组
     *
     * @param systemAppCommonGroup 应用关联的公共组
     * @return 结果
     */
    public SystemAppCommonGroup updateSystemAppCommonGroup(SystemAppCommonGroup systemAppCommonGroup);

    /**
     * 批量删除应用关联的公共组
     *
     * @param ids 需要删除的应用关联的公共组主键集合
     * @return 结果
     */
    public int deleteSystemAppCommonGroupByIds(String[] ids);

    /**
     * 删除应用关联的公共组信息
     *
     * @param id 应用关联的公共组主键
     * @return 结果
     */
    public int deleteSystemAppCommonGroupById(String id);

}
