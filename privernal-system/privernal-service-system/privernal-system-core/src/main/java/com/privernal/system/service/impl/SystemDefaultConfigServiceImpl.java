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
import com.privernal.system.domain.SystemDefaultConfig;
import com.privernal.system.dto.AppSystemConfigDTO;
import com.privernal.system.filter.SystemDefaultConfigFilterDTO;
import com.privernal.system.mapper.SystemDefaultConfigMapper;
import com.privernal.system.service.ISystemDefaultConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统配置的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 系统配置的Service业务层处理
 */
@Slf4j
@Service
public class SystemDefaultConfigServiceImpl implements ISystemDefaultConfigService {

    @Autowired
    private SystemDefaultConfigMapper systemDefaultConfigMapper;

    /**
     * 查询系统配置
     *
     * @param id 系统配置主键
     * @return 系统配置
     */
    @Transactional(readOnly = true)
    @Override
    public SystemDefaultConfig selectSystemDefaultConfigById(String id) {
        return systemDefaultConfigMapper.selectById(id);
    }

    /**
     * 查询系统配置列表
     *
     * @param systemDefaultConfig 系统配置
     * @return 系统配置
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemDefaultConfig> selectSystemDefaultConfigList(SystemDefaultConfigFilterDTO systemDefaultConfig) {
        return systemDefaultConfigMapper.selectSystemDefaultConfigList(systemDefaultConfig);
    }

    /**
     * 新增系统配置
     *
     * @param systemDefaultConfig 系统配置
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemDefaultConfig(SystemDefaultConfig systemDefaultConfig) {
        return systemDefaultConfigMapper.insert(systemDefaultConfig);
    }

    /**
     * 修改系统配置
     *
     * @param systemDefaultConfig 系统配置
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemDefaultConfig(SystemDefaultConfig systemDefaultConfig) {
        return systemDefaultConfigMapper.updateById(systemDefaultConfig);
    }

    /**
     * 批量删除系统配置
     *
     * @param ids 需要删除的系统配置主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemDefaultConfigByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemDefaultConfigById(id);
        }
        return ids.length;
    }

    /**
     * 删除系统配置信息
     *
     * @param id 系统配置主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemDefaultConfigById(String id) {
        return systemDefaultConfigMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemDefaultConfig> selectSystemDefaultConfigList(String key) {
        return systemDefaultConfigMapper.selectConfigListByKey(key);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemDefaultConfig<AppSystemConfigDTO>> selectNetConfigList() {
        return systemDefaultConfigMapper.selectConfigNetAddressList();
    }
}
