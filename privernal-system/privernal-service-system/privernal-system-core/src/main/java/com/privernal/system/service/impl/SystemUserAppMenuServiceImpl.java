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

package com.privernal.system.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.system.domain.SystemUserAppMenu;
import com.privernal.system.dto.SystemUserAppMenuDTO;
import com.privernal.system.dto.SystemUserAppMenuDetailDTO;
import com.privernal.system.filter.SystemUserAppMenuFilterDTO;
import com.privernal.system.mapper.SystemUserAppMenuMapper;
import com.privernal.system.service.ISystemUserAppMenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户收藏的常用功能菜单的Service业务层处理
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 用户收藏的常用功能菜单的Service业务层处理
 */
@Slf4j
@Service
public class SystemUserAppMenuServiceImpl implements ISystemUserAppMenuService {
    @Autowired
    private SystemUserAppMenuMapper systemUserAppMenuMapper;

    /**
     * 查询用户收藏的常用功能菜单
     *
     * @param id 用户收藏的常用功能菜单主键
     * @return 用户收藏的常用功能菜单
     */
    @Transactional(readOnly = true)
    @Override
    public SystemUserAppMenu selectSystemUserAppMenuById(String id) {
        return systemUserAppMenuMapper.selectById(id);
    }

    /**
     * 查询用户收藏的常用功能菜单
     *
     * @param id 用户收藏的常用功能菜单主键
     * @return 用户收藏的常用功能菜单
     */
    @Transactional(readOnly = true)
    @Override
    public SystemUserAppMenuDetailDTO selectSystemUserAppMenuDetailDTOById(String id) {
        SystemUserAppMenu systemUserAppMenu = systemUserAppMenuMapper.selectById(id);
        SystemUserAppMenuDetailDTO systemUserAppMenuDetailDTO = new SystemUserAppMenuDetailDTO();
        if (systemUserAppMenu != null) {
            BeanUtils.copyProperties(systemUserAppMenu, systemUserAppMenuDetailDTO);
        }
        return systemUserAppMenuDetailDTO;
    }

    /**
     * 查询用户收藏的常用功能菜单列表
     *
     * @param systemUserAppMenu 用户收藏的常用功能菜单
     * @return 用户收藏的常用功能菜单
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserAppMenuDTO> selectSystemUserAppMenuList(SystemUserAppMenuFilterDTO systemUserAppMenu) {
        return systemUserAppMenuMapper.selectSystemUserAppMenuList(systemUserAppMenu);
    }

    /**
     * 新增用户收藏的常用功能菜单
     *
     * @param systemUserAppMenu 用户收藏的常用功能菜单
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemUserAppMenu insertSystemUserAppMenu(SystemUserAppMenu systemUserAppMenu) {
        systemUserAppMenuMapper.insert(systemUserAppMenu);
        return systemUserAppMenu;
    }

    /**
     * 修改用户收藏的常用功能菜单
     *
     * @param systemUserAppMenu 用户收藏的常用功能菜单
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemUserAppMenu updateSystemUserAppMenu(SystemUserAppMenu systemUserAppMenu) {
        systemUserAppMenuMapper.updateById(systemUserAppMenu);
        return systemUserAppMenu;
    }

    /**
     * 批量删除用户收藏的常用功能菜单
     *
     * @param ids 需要删除的用户收藏的常用功能菜单主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserAppMenuByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemUserAppMenuById(id);
        }
        return ids.length;
    }

    /**
     * 删除用户收藏的常用功能菜单信息
     *
     * @param id 用户收藏的常用功能菜单主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserAppMenuById(String id) {
        return systemUserAppMenuMapper.deleteFillById(id);
    }

}
