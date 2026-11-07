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
import com.privernal.system.domain.SystemAppMenu;
import com.privernal.system.dto.SystemAppMenuDTO;
import com.privernal.system.dto.SystemAppMenuDetailDTO;
import com.privernal.system.filter.SystemAppMenuFilterDTO;
import com.privernal.system.mapper.SystemAppMenuMapper;
import com.privernal.system.service.ISystemAppMenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 应用关联的菜单的Service业务层处理
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用关联的菜单的Service业务层处理
 */
@Slf4j
@Service
public class SystemAppMenuServiceImpl implements ISystemAppMenuService {
    @Autowired
    private SystemAppMenuMapper systemAppMenuMapper;

    /**
     * 查询应用关联的菜单
     *
     * @param id 应用关联的菜单主键
     * @return 应用关联的菜单
     */
    @Transactional(readOnly = true)
    @Override
    public SystemAppMenu selectSystemAppMenuById(String id) {
        return systemAppMenuMapper.selectById(id);
    }

    /**
     * 查询应用关联的菜单
     *
     * @param id 应用关联的菜单主键
     * @return 应用关联的菜单
     */
    @Transactional(readOnly = true)
    @Override
    public SystemAppMenuDetailDTO selectSystemAppMenuDetailDTOById(String id) {
        SystemAppMenu systemAppMenu = systemAppMenuMapper.selectById(id);
        SystemAppMenuDetailDTO systemAppMenuDetailDTO = new SystemAppMenuDetailDTO();
        if (systemAppMenu != null) {
            BeanUtils.copyProperties(systemAppMenu, systemAppMenuDetailDTO);
        }
        return systemAppMenuDetailDTO;
    }

    /**
     * 查询应用关联的菜单列表
     *
     * @param systemAppMenu 应用关联的菜单
     * @return 应用关联的菜单
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemAppMenuDTO> selectSystemAppMenuList(SystemAppMenuFilterDTO systemAppMenu) {
        return systemAppMenuMapper.selectSystemAppMenuList(systemAppMenu);
    }

    /**
     * 新增应用关联的菜单
     *
     * @param systemAppMenu 应用关联的菜单
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemAppMenu insertSystemAppMenu(SystemAppMenu systemAppMenu) {
        systemAppMenuMapper.insert(systemAppMenu);
        return systemAppMenu;
    }

    /**
     * 修改应用关联的菜单
     *
     * @param systemAppMenu 应用关联的菜单
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemAppMenu updateSystemAppMenu(SystemAppMenu systemAppMenu) {
        systemAppMenuMapper.updateById(systemAppMenu);
        return systemAppMenu;
    }

    /**
     * 批量删除应用关联的菜单
     *
     * @param ids 需要删除的应用关联的菜单主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemAppMenuByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemAppMenuById(id);
        }
        return ids.length;
    }

    /**
     * 删除应用关联的菜单信息
     *
     * @param id 应用关联的菜单主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemAppMenuById(String id) {
        return systemAppMenuMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemAppMenuDTO> selectAppMenuListByAppId(SystemAppMenuFilterDTO systemAppMenu) {
        return systemAppMenuMapper.selectAppMenuListByAppId(systemAppMenu);
    }

}
