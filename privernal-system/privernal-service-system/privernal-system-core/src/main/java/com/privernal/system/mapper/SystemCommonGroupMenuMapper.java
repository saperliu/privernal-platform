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
import com.privernal.system.domain.SystemCommonGroupMenu;
import com.privernal.system.dto.SystemCommonGroupMenuDTO;
import com.privernal.system.filter.SystemCommonGroupMenuFilterDTO;

import java.util.List;

/**
 * 组关联的权限Mapper接口
 *
 * @author saperliu
 * @date 2024-07-10
 * @Description 组关联的权限Mapper接口
 */
//@PrivernalDataScope(permissionMain = "system:commonGroupMenu")
@IgnoreDataScope
public interface SystemCommonGroupMenuMapper extends PrivernalMapper<SystemCommonGroupMenu> {
    /**
     * 查询组关联的权限列表
     *
     * @param systemCommonGroupMenu 组关联的权限
     * @return 组关联的权限集合
     */
//    @PrivernalDataScope(mainAlias = "commonGroupMenu", permission = "system:commonGroupMenu:list")
    public List<SystemCommonGroupMenuDTO> selectSystemCommonGroupMenuList(SystemCommonGroupMenuFilterDTO systemCommonGroupMenu);


    /**
     * 通过组ID，查询此组的默认的菜单(权限)
     *
     * @param systemCommonGroupMenu 组关联的权限
     * @return
     */
    public List<SystemCommonGroupMenuDTO> selectCommGroupMenuListByCommonGroupId(SystemCommonGroupMenuFilterDTO systemCommonGroupMenu);
}
