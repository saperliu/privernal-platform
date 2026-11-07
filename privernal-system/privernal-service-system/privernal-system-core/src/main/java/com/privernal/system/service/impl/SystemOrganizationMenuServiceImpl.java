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
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.domain.SystemOrganizationMenu;
import com.privernal.system.dto.SystemOrganizationMenuDTO;
import com.privernal.system.filter.SystemOrganizationMenuFilterDTO;
import com.privernal.system.mapper.SystemOrganizationMenuMapper;
import com.privernal.system.service.ISystemOrganizationMenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 组织拥有的菜单的Service业务层处理
 *
 * @author saperliu
 * @date 2023-04-17
 * @Description 组织拥有的菜单的Service业务层处理
 */
@Slf4j
@Service
public class SystemOrganizationMenuServiceImpl implements ISystemOrganizationMenuService {
    @Autowired
    private SystemOrganizationMenuMapper systemOrganizationMenuMapper;

    /**
     * 查询组织拥有的菜单
     *
     * @param id 组织拥有的菜单主键
     * @return 组织拥有的菜单
     */
    @Transactional(readOnly = true)
    @Override
    public SystemOrganizationMenu selectSystemOrganizationMenuById(String id) {
        return systemOrganizationMenuMapper.selectById(id);
    }

    /**
     * 查询组织拥有的菜单列表
     *
     * @param systemOrganizationMenu 组织拥有的菜单
     * @return 组织拥有的菜单
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemOrganizationMenuDTO> selectSystemOrganizationMenuList(SystemOrganizationMenuFilterDTO systemOrganizationMenu) {
        if (SecurityUtils.isSuperRoleUser() || SecurityUtils.isSuperPlatformRoleUser()) {
            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                return systemOrganizationMenuMapper.selectMenuAllByOrganizationId(systemOrganizationMenu.getOrganizationId());
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }
        }
        return systemOrganizationMenuMapper.selectSystemOrganizationMenuList(systemOrganizationMenu);
    }

    /**
     * 新增组织拥有的菜单
     *
     * @param systemOrganizationMenu 组织拥有的菜单
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemOrganizationMenu(SystemOrganizationMenu systemOrganizationMenu) {
        return systemOrganizationMenuMapper.insert(systemOrganizationMenu);
    }

    /**
     * 修改组织拥有的菜单
     *
     * @param systemOrganizationMenu 组织拥有的菜单
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemOrganizationMenu(SystemOrganizationMenu systemOrganizationMenu) {
        return systemOrganizationMenuMapper.updateById(systemOrganizationMenu);
    }

    /**
     * 批量删除组织拥有的菜单
     *
     * @param ids 需要删除的组织拥有的菜单主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemOrganizationMenuByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemOrganizationMenuById(id);
        }
        return ids.length;
    }

    /**
     * 删除组织拥有的菜单信息
     *
     * @param id 组织拥有的菜单主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemOrganizationMenuById(String id) {
        return systemOrganizationMenuMapper.deleteFillById(id);
    }

}
