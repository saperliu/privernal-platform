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
import com.privernal.system.domain.SystemOrganizationApp;
import com.privernal.system.dto.SystemOrganizationAppDTO;
import com.privernal.system.dto.SystemOrganizationAppDetailDTO;
import com.privernal.system.filter.SystemOrganizationAppFilterDTO;
import com.privernal.system.mapper.SystemOrganizationAppMapper;
import com.privernal.system.service.ISystemOrganizationAppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 组织授权的应用的Service业务层处理
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 组织授权的应用的Service业务层处理
 */
@Slf4j
@Service
public class SystemOrganizationAppServiceImpl implements ISystemOrganizationAppService {
    @Autowired
    private SystemOrganizationAppMapper systemOrganizationAppMapper;

    /**
     * 查询组织授权的应用
     *
     * @param id 组织授权的应用主键
     * @return 组织授权的应用
     */
    @Transactional(readOnly = true)
    @Override
    public SystemOrganizationApp selectSystemOrganizationAppById(String id) {
        return systemOrganizationAppMapper.selectById(id);
    }

    /**
     * 查询组织授权的应用
     *
     * @param id 组织授权的应用主键
     * @return 组织授权的应用
     */
    @Transactional(readOnly = true)
    @Override
    public SystemOrganizationAppDetailDTO selectSystemOrganizationAppDetailDTOById(String id) {
        SystemOrganizationApp systemOrganizationApp = systemOrganizationAppMapper.selectById(id);
        SystemOrganizationAppDetailDTO systemOrganizationAppDetailDTO = new SystemOrganizationAppDetailDTO();
        if (systemOrganizationApp != null) {
            BeanUtils.copyProperties(systemOrganizationApp, systemOrganizationAppDetailDTO);
        }
        return systemOrganizationAppDetailDTO;
    }

    /**
     * 查询组织授权的应用列表
     *
     * @param systemOrganizationApp 组织授权的应用
     * @return 组织授权的应用
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemOrganizationAppDTO> selectSystemOrganizationAppList(SystemOrganizationAppFilterDTO systemOrganizationApp) {
        return systemOrganizationAppMapper.selectSystemOrganizationAppList(systemOrganizationApp);
    }

    /**
     * 新增组织授权的应用
     *
     * @param systemOrganizationApp 组织授权的应用
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemOrganizationApp insertSystemOrganizationApp(SystemOrganizationApp systemOrganizationApp) {
        systemOrganizationAppMapper.insert(systemOrganizationApp);
        return systemOrganizationApp;
    }

    /**
     * 修改组织授权的应用
     *
     * @param systemOrganizationApp 组织授权的应用
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemOrganizationApp updateSystemOrganizationApp(SystemOrganizationApp systemOrganizationApp) {
        systemOrganizationAppMapper.updateById(systemOrganizationApp);
        return systemOrganizationApp;
    }

    /**
     * 批量删除组织授权的应用
     *
     * @param ids 需要删除的组织授权的应用主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemOrganizationAppByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemOrganizationAppById(id);
        }
        return ids.length;
    }

    /**
     * 删除组织授权的应用信息
     *
     * @param id 组织授权的应用主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemOrganizationAppById(String id) {
        return systemOrganizationAppMapper.deleteFillById(id);
    }

}
