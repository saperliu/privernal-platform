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

import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.system.domain.SystemCommonGroupMenu;
import com.privernal.system.dto.SystemCommonGroupMenuDTO;
import com.privernal.system.dto.SystemCommonGroupMenuDetailDTO;
import com.privernal.system.filter.SystemCommonGroupMenuFilterDTO;
import com.privernal.system.mapper.SystemCommonGroupMenuMapper;
import com.privernal.system.service.ISystemCommonGroupMenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 组关联的权限的Service业务层处理
 *
 * @author saperliu
 * @date 2024-07-10
 * @Description 组关联的权限的Service业务层处理
 */
@Slf4j
@Service
public class SystemCommonGroupMenuServiceImpl implements ISystemCommonGroupMenuService {
    @Autowired
    private SystemCommonGroupMenuMapper systemCommonGroupMenuMapper;

    /**
     * 查询组关联的权限
     *
     * @param id 组关联的权限主键
     * @return 组关联的权限
     */
    @Transactional(readOnly = true)
    @Override
    public SystemCommonGroupMenu selectSystemCommonGroupMenuById(String id) {
        return systemCommonGroupMenuMapper.selectById(id);
    }

    /**
     * 查询组关联的权限
     *
     * @param id 组关联的权限主键
     * @return 组关联的权限
     */
    @Transactional(readOnly = true)
    @Override
    public SystemCommonGroupMenuDetailDTO selectSystemCommonGroupMenuDetailDTOById(String id) {
        SystemCommonGroupMenu systemCommonGroupMenu = systemCommonGroupMenuMapper.selectById(id);
        SystemCommonGroupMenuDetailDTO systemCommonGroupMenuDetailDTO = new SystemCommonGroupMenuDetailDTO();
        if (systemCommonGroupMenu != null) {
            BeanUtils.copyProperties(systemCommonGroupMenu, systemCommonGroupMenuDetailDTO, DataConstants.IGNORED_ON_COPY);
        }
        return systemCommonGroupMenuDetailDTO;
    }

    /**
     * 查询组关联的权限列表
     *
     * @param systemCommonGroupMenu 组关联的权限
     * @return 组关联的权限
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemCommonGroupMenuDTO> selectSystemCommonGroupMenuList(SystemCommonGroupMenuFilterDTO systemCommonGroupMenu) {
        return systemCommonGroupMenuMapper.selectSystemCommonGroupMenuList(systemCommonGroupMenu);
    }

    /**
     * 新增组关联的权限
     *
     * @param systemCommonGroupMenu 组关联的权限
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemCommonGroupMenu insertSystemCommonGroupMenu(SystemCommonGroupMenu systemCommonGroupMenu) {
        systemCommonGroupMenuMapper.insert(systemCommonGroupMenu);
        return systemCommonGroupMenu;
    }

    /**
     * 修改组关联的权限
     *
     * @param systemCommonGroupMenu 组关联的权限
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemCommonGroupMenu updateSystemCommonGroupMenu(SystemCommonGroupMenu systemCommonGroupMenu) {
        systemCommonGroupMenuMapper.updateById(systemCommonGroupMenu);
        return systemCommonGroupMenu;
    }

    /**
     * 批量删除组关联的权限
     *
     * @param ids 需要删除的组关联的权限主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemCommonGroupMenuByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemCommonGroupMenuById(id);
        }
        return ids.length;
    }

    /**
     * 删除组关联的权限信息
     *
     * @param id 组关联的权限主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemCommonGroupMenuById(String id) {
        return systemCommonGroupMenuMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemCommonGroupMenuDTO> selectCommGroupMenuListByCommonGroupId(SystemCommonGroupMenuFilterDTO systemCommonGroupMenu) {
        return systemCommonGroupMenuMapper.selectCommGroupMenuListByCommonGroupId(systemCommonGroupMenu);
    }
}
