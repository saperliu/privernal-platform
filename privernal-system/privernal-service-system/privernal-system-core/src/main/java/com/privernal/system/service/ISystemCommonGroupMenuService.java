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

import com.privernal.system.domain.SystemCommonGroupMenu;
import com.privernal.system.dto.SystemCommonGroupMenuDTO;
import com.privernal.system.dto.SystemCommonGroupMenuDetailDTO;
import com.privernal.system.filter.SystemCommonGroupMenuFilterDTO;

import java.util.List;

/**
 * 组关联的权限的Service接口
 *
 * @author saperliu
 * @date 2024-07-10
 * @Description 组关联的权限的Service接口
 */
public interface ISystemCommonGroupMenuService {
    /**
     * 查询组关联的权限
     *
     * @param id 组关联的权限主键
     * @return 组关联的权限
     */
    public SystemCommonGroupMenu selectSystemCommonGroupMenuById(String id);

    /**
     * 查询组关联的权限
     *
     * @param id 组关联的权限主键
     * @return 组关联的权限
     */
    public SystemCommonGroupMenuDetailDTO selectSystemCommonGroupMenuDetailDTOById(String id);

    /**
     * 查询组关联的权限列表
     *
     * @param systemCommonGroupMenu 组关联的权限
     * @return 组关联的权限集合
     */
    public List<SystemCommonGroupMenuDTO> selectSystemCommonGroupMenuList(SystemCommonGroupMenuFilterDTO systemCommonGroupMenu);

    /**
     * 新增组关联的权限
     *
     * @param systemCommonGroupMenu 组关联的权限
     * @return 结果
     */
    public SystemCommonGroupMenu insertSystemCommonGroupMenu(SystemCommonGroupMenu systemCommonGroupMenu);

    /**
     * 修改组关联的权限
     *
     * @param systemCommonGroupMenu 组关联的权限
     * @return 结果
     */
    public SystemCommonGroupMenu updateSystemCommonGroupMenu(SystemCommonGroupMenu systemCommonGroupMenu);

    /**
     * 批量删除组关联的权限
     *
     * @param ids 需要删除的组关联的权限主键集合
     * @return 结果
     */
    public int deleteSystemCommonGroupMenuByIds(String[] ids);

    /**
     * 删除组关联的权限信息
     *
     * @param id 组关联的权限主键
     * @return 结果
     */
    public int deleteSystemCommonGroupMenuById(String id);

    /**
     * 查询公共组的功能菜单树
     *
     * @param systemCommonGroupMenu
     * @return
     */
    public List<SystemCommonGroupMenuDTO> selectCommGroupMenuListByCommonGroupId(SystemCommonGroupMenuFilterDTO systemCommonGroupMenu);

}
