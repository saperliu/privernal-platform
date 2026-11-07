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
import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemAppMenu;
import com.privernal.system.dto.SystemAppMenuDTO;
import com.privernal.system.filter.SystemAppMenuFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 应用关联的菜单Mapper接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用关联的菜单Mapper接口
 */
//@PrivernalDataScope(mainAlias = "appMenu",permissionMain = "system:appMenu",ignoreDepartment = true)
@IgnoreDataScope
public interface SystemAppMenuMapper extends PrivernalMapper<SystemAppMenu> {
    /**
     * 查询应用关联的菜单列表
     *
     * @param systemAppMenu 应用关联的菜单
     * @return 应用关联的菜单集合
     */
    @PrivernalDataScope(mainAlias = "appMenu", permission = "system:appMenu:list")
    public List<SystemAppMenuDTO> selectSystemAppMenuList(SystemAppMenuFilterDTO systemAppMenu);

    /**
     * 通过APPId 查询菜单ID
     * 用于路由的查询
     *
     * @param appId 应用的ID
     * @return
     */
    @IgnoreDataScope
    public List<SystemAppMenuDTO> selectSystemAppMenuListByAppId(@Param("appId") String appId);

    /**
     * 通过应用的编码查询功能菜单
     * 用于路由的查询
     *
     * @param appCode
     * @return
     */
    @IgnoreDataScope
    public List<SystemAppMenuDTO> selectSystemAppMenuListByAppCode(@Param("appCode") String appCode);

    /**
     * 查询应用的功能菜单树
     *
     * @param systemAppMenu
     * @return
     */
    public List<SystemAppMenuDTO> selectAppMenuListByAppId(SystemAppMenuFilterDTO systemAppMenu);
}
