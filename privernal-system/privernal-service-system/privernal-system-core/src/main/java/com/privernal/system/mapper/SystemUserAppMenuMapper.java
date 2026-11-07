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
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemUserAppMenu;
import com.privernal.system.dto.SystemUserAppMenuDTO;
import com.privernal.system.filter.SystemUserAppMenuFilterDTO;

import java.util.List;

/**
 * 用户收藏的常用功能菜单Mapper接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 用户收藏的常用功能菜单Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:userAppMenu")
public interface SystemUserAppMenuMapper extends PrivernalMapper<SystemUserAppMenu> {
    /**
     * 查询用户收藏的常用功能菜单列表
     *
     * @param systemUserAppMenu 用户收藏的常用功能菜单
     * @return 用户收藏的常用功能菜单集合
     */
    @PrivernalDataScope(mainAlias = "userAppMenu", permission = "system:userAppMenu:list")
    public List<SystemUserAppMenuDTO> selectSystemUserAppMenuList(SystemUserAppMenuFilterDTO systemUserAppMenu);

}
