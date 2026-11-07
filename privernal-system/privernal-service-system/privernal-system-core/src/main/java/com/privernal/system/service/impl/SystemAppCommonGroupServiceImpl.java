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
import com.privernal.system.domain.SystemAppCommonGroup;
import com.privernal.system.dto.SystemAppCommonGroupDTO;
import com.privernal.system.dto.SystemAppCommonGroupDetailDTO;
import com.privernal.system.filter.SystemAppCommonGroupFilterDTO;
import com.privernal.system.mapper.SystemAppCommonGroupMapper;
import com.privernal.system.service.ISystemAppCommonGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 应用关联的公共组的Service业务层处理
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用关联的公共组的Service业务层处理
 */
@Slf4j
@Service
public class SystemAppCommonGroupServiceImpl implements ISystemAppCommonGroupService {
    @Autowired
    private SystemAppCommonGroupMapper systemAppCommonGroupMapper;

    /**
     * 查询应用关联的公共组
     *
     * @param id 应用关联的公共组主键
     * @return 应用关联的公共组
     */
    @Transactional(readOnly = true)
    @Override
    public SystemAppCommonGroup selectSystemAppCommonGroupById(String id) {
        return systemAppCommonGroupMapper.selectById(id);
    }

    /**
     * 查询应用关联的公共组
     *
     * @param id 应用关联的公共组主键
     * @return 应用关联的公共组
     */
    @Transactional(readOnly = true)
    @Override
    public SystemAppCommonGroupDetailDTO selectSystemAppCommonGroupDetailDTOById(String id) {
        SystemAppCommonGroup systemAppCommonGroup = systemAppCommonGroupMapper.selectById(id);
        SystemAppCommonGroupDetailDTO systemAppCommonGroupDetailDTO = new SystemAppCommonGroupDetailDTO();
        if (systemAppCommonGroup != null) {
            BeanUtils.copyProperties(systemAppCommonGroup, systemAppCommonGroupDetailDTO);
        }
        return systemAppCommonGroupDetailDTO;
    }

    /**
     * 查询应用关联的公共组列表
     *
     * @param systemAppCommonGroup 应用关联的公共组
     * @return 应用关联的公共组
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemAppCommonGroupDTO> selectSystemAppCommonGroupList(SystemAppCommonGroupFilterDTO systemAppCommonGroup) {
        return systemAppCommonGroupMapper.selectSystemAppCommonGroupList(systemAppCommonGroup);
    }

    /**
     * 新增应用关联的公共组
     *
     * @param systemAppCommonGroup 应用关联的公共组
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemAppCommonGroup insertSystemAppCommonGroup(SystemAppCommonGroup systemAppCommonGroup) {
        systemAppCommonGroupMapper.insert(systemAppCommonGroup);
        return systemAppCommonGroup;
    }

    /**
     * 修改应用关联的公共组
     *
     * @param systemAppCommonGroup 应用关联的公共组
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemAppCommonGroup updateSystemAppCommonGroup(SystemAppCommonGroup systemAppCommonGroup) {
        systemAppCommonGroupMapper.updateById(systemAppCommonGroup);
        return systemAppCommonGroup;
    }

    /**
     * 批量删除应用关联的公共组
     *
     * @param ids 需要删除的应用关联的公共组主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemAppCommonGroupByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemAppCommonGroupById(id);
        }
        return ids.length;
    }

    /**
     * 删除应用关联的公共组信息
     *
     * @param id 应用关联的公共组主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemAppCommonGroupById(String id) {
        return systemAppCommonGroupMapper.deleteFillById(id);
    }

}
