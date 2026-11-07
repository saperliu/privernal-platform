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

import com.privernal.system.domain.SystemUserAppMenu;
import com.privernal.system.dto.SystemUserAppMenuDTO;
import com.privernal.system.dto.SystemUserAppMenuDetailDTO;
import com.privernal.system.filter.SystemUserAppMenuFilterDTO;

import java.util.List;

/**
 * 用户收藏的常用功能菜单的Service接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 用户收藏的常用功能菜单的Service接口
 */
public interface ISystemUserAppMenuService {
    /**
     * 查询用户收藏的常用功能菜单
     *
     * @param id 用户收藏的常用功能菜单主键
     * @return 用户收藏的常用功能菜单
     */
    public SystemUserAppMenu selectSystemUserAppMenuById(String id);

    /**
     * 查询用户收藏的常用功能菜单
     *
     * @param id 用户收藏的常用功能菜单主键
     * @return 用户收藏的常用功能菜单
     */
    public SystemUserAppMenuDetailDTO selectSystemUserAppMenuDetailDTOById(String id);

    /**
     * 查询用户收藏的常用功能菜单列表
     *
     * @param systemUserAppMenu 用户收藏的常用功能菜单
     * @return 用户收藏的常用功能菜单集合
     */
    public List<SystemUserAppMenuDTO> selectSystemUserAppMenuList(SystemUserAppMenuFilterDTO systemUserAppMenu);

    /**
     * 新增用户收藏的常用功能菜单
     *
     * @param systemUserAppMenu 用户收藏的常用功能菜单
     * @return 结果
     */
    public SystemUserAppMenu insertSystemUserAppMenu(SystemUserAppMenu systemUserAppMenu);

    /**
     * 修改用户收藏的常用功能菜单
     *
     * @param systemUserAppMenu 用户收藏的常用功能菜单
     * @return 结果
     */
    public SystemUserAppMenu updateSystemUserAppMenu(SystemUserAppMenu systemUserAppMenu);

    /**
     * 批量删除用户收藏的常用功能菜单
     *
     * @param ids 需要删除的用户收藏的常用功能菜单主键集合
     * @return 结果
     */
    public int deleteSystemUserAppMenuByIds(String[] ids);

    /**
     * 删除用户收藏的常用功能菜单信息
     *
     * @param id 用户收藏的常用功能菜单主键
     * @return 结果
     */
    public int deleteSystemUserAppMenuById(String id);

}
