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

import com.privernal.system.domain.SystemAppMenu;
import com.privernal.system.dto.SystemAppMenuDTO;
import com.privernal.system.dto.SystemAppMenuDetailDTO;
import com.privernal.system.filter.SystemAppMenuFilterDTO;

import java.util.List;

/**
 * 应用关联的菜单的Service接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用关联的菜单的Service接口
 */
public interface ISystemAppMenuService {
    /**
     * 查询应用关联的菜单
     *
     * @param id 应用关联的菜单主键
     * @return 应用关联的菜单
     */
    public SystemAppMenu selectSystemAppMenuById(String id);

    /**
     * 查询应用关联的菜单
     *
     * @param id 应用关联的菜单主键
     * @return 应用关联的菜单
     */
    public SystemAppMenuDetailDTO selectSystemAppMenuDetailDTOById(String id);

    /**
     * 查询应用关联的菜单列表
     *
     * @param systemAppMenu 应用关联的菜单
     * @return 应用关联的菜单集合
     */
    public List<SystemAppMenuDTO> selectSystemAppMenuList(SystemAppMenuFilterDTO systemAppMenu);

    /**
     * 新增应用关联的菜单
     *
     * @param systemAppMenu 应用关联的菜单
     * @return 结果
     */
    public SystemAppMenu insertSystemAppMenu(SystemAppMenu systemAppMenu);

    /**
     * 修改应用关联的菜单
     *
     * @param systemAppMenu 应用关联的菜单
     * @return 结果
     */
    public SystemAppMenu updateSystemAppMenu(SystemAppMenu systemAppMenu);

    /**
     * 批量删除应用关联的菜单
     *
     * @param ids 需要删除的应用关联的菜单主键集合
     * @return 结果
     */
    public int deleteSystemAppMenuByIds(String[] ids);

    /**
     * 删除应用关联的菜单信息
     *
     * @param id 应用关联的菜单主键
     * @return 结果
     */
    public int deleteSystemAppMenuById(String id);

    /**
     * 查询应用的功能菜单树
     *
     * @param systemAppMenu
     * @return
     */
    public List<SystemAppMenuDTO> selectAppMenuListByAppId(SystemAppMenuFilterDTO systemAppMenu);

}
