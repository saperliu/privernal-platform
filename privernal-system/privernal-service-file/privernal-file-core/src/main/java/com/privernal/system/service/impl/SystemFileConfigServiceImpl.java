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
import com.privernal.system.domain.SystemFileConfig;
import com.privernal.system.dto.SystemFileConfigDTO;
import com.privernal.system.filter.SystemFileConfigFilterDTO;
import com.privernal.system.mapper.SystemFileConfigMapper;
import com.privernal.system.service.ISystemFileConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文件配置的Service业务层处理
 *
 * @author saperliu
 * @date 2023-10-16
 * @Description 文件配置的Service业务层处理
 */
@Slf4j
@Service
public class SystemFileConfigServiceImpl implements ISystemFileConfigService {
    @Autowired
    private SystemFileConfigMapper systemFileConfigMapper;

    /**
     * 查询文件配置
     *
     * @param id 文件配置主键
     * @return 文件配置
     */
    @Transactional(readOnly = true)
    @Override
    public SystemFileConfig selectSystemFileConfigById(String id) {
        return systemFileConfigMapper.selectById(id);
    }

    /**
     * 查询文件配置列表
     *
     * @param systemFileConfig 文件配置
     * @return 文件配置
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemFileConfigDTO> selectSystemFileConfigList(SystemFileConfigFilterDTO systemFileConfig) {
        return systemFileConfigMapper.selectSystemFileConfigList(systemFileConfig);
    }

    /**
     * 新增文件配置
     *
     * @param systemFileConfig 文件配置
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemFileConfig(SystemFileConfig systemFileConfig) {
        return systemFileConfigMapper.insert(systemFileConfig);
    }

    /**
     * 修改文件配置
     *
     * @param systemFileConfig 文件配置
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemFileConfig(SystemFileConfig systemFileConfig) {
        return systemFileConfigMapper.updateById(systemFileConfig);
    }

    /**
     * 批量删除文件配置
     *
     * @param ids 需要删除的文件配置主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemFileConfigByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemFileConfigById(id);
        }
        return ids.length;
    }

    /**
     * 删除文件配置信息
     *
     * @param id 文件配置主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemFileConfigById(String id) {
        return systemFileConfigMapper.deleteFillById(id);
    }

}
