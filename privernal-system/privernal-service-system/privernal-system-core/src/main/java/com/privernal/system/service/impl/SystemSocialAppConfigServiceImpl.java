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
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.system.domain.SystemSocialAppConfig;
import com.privernal.system.dto.SystemSocialAppConfigDTO;
import com.privernal.system.filter.SystemSocialAppConfigFilterDTO;
import com.privernal.system.mapper.SystemSocialAppConfigMapper;
import com.privernal.system.service.ISystemSocialAppConfigService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.privernal.common.core.constant.DataConstants.DATA_DELETE_FLAG_NOT;

/**
 * 第三方应用配置(小程序)的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 第三方应用配置(小程序)的Service业务层处理
 */
@Slf4j
@Service
public class SystemSocialAppConfigServiceImpl implements ISystemSocialAppConfigService {
    @Autowired
    private SystemSocialAppConfigMapper systemSocialAppConfigMapper;

    /**
     * 查询第三方应用配置(小程序)
     *
     * @param id 第三方应用配置(小程序)主键
     * @return 第三方应用配置(小程序)
     */
    @Transactional(readOnly = true)
    @Override
    public SystemSocialAppConfig selectSystemSocialAppConfigById(String id) {
        return systemSocialAppConfigMapper.selectById(id);
    }

    /**
     * 查询第三方应用配置(小程序)列表
     *
     * @param systemSocialAppConfig 第三方应用配置(小程序)
     * @return 第三方应用配置(小程序)
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemSocialAppConfig> selectSystemSocialAppConfigList(SystemSocialAppConfigFilterDTO systemSocialAppConfig) {
        return systemSocialAppConfigMapper.selectSystemSocialAppConfigList(systemSocialAppConfig);
    }

    /**
     * 新增第三方应用配置(小程序)
     *
     * @param systemSocialAppConfig 第三方应用配置(小程序)
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemSocialAppConfig(SystemSocialAppConfig systemSocialAppConfig) {
        return systemSocialAppConfigMapper.insert(systemSocialAppConfig);
    }

    /**
     * 修改第三方应用配置(小程序)
     *
     * @param systemSocialAppConfig 第三方应用配置(小程序)
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemSocialAppConfig(SystemSocialAppConfig systemSocialAppConfig) {
        return systemSocialAppConfigMapper.updateById(systemSocialAppConfig);
    }

    /**
     * 批量删除第三方应用配置(小程序)
     *
     * @param ids 需要删除的第三方应用配置(小程序)主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemSocialAppConfigByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemSocialAppConfigById(id);
        }
        return ids.length;
    }

    /**
     * 删除第三方应用配置(小程序)信息
     *
     * @param id 第三方应用配置(小程序)主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemSocialAppConfigById(String id) {
        return systemSocialAppConfigMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemSocialAppConfig selectSysSocialAppConfigByCode(String appCode) {
        QueryWrapper<SystemSocialAppConfig> queryWrapper = new QueryWrapper<SystemSocialAppConfig>();
        queryWrapper.eq("app_code", appCode);
        queryWrapper.eq("deleted_flag", DATA_DELETE_FLAG_NOT);
        return systemSocialAppConfigMapper.selectOne(queryWrapper);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemSocialAppConfig selectSysSocialAppConfigByCodeAndOrganizationId(String appCode, String organizationId) {
        QueryWrapper<SystemSocialAppConfig> queryWrapper = new QueryWrapper<SystemSocialAppConfig>();
        queryWrapper.eq("app_code", appCode);
        queryWrapper.eq("organization_id", organizationId);
        queryWrapper.eq("deleted_flag", DATA_DELETE_FLAG_NOT);
        return systemSocialAppConfigMapper.selectOne(queryWrapper);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemSocialAppConfig selectSysSocialAppConfigByCodeAndSourceAndOrganizationId(String appCode, String source, String organizationId) {
        LambdaPrivernalWrapper<SystemSocialAppConfig> queryWrapper = new LambdaPrivernalWrapper<SystemSocialAppConfig>();
        queryWrapper.eq(SystemSocialAppConfig::getSource, source);
        queryWrapper.eq(SystemSocialAppConfig::getAppCode, appCode);
        queryWrapper.eq(SystemSocialAppConfig::getOrganizationId, organizationId);
        return systemSocialAppConfigMapper.selectOne(queryWrapper);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemSocialAppConfig selectSysSocialAppConfigByCodeAndCropId(String appCode, String corpId) {
        QueryWrapper<SystemSocialAppConfig> queryWrapper = new QueryWrapper<SystemSocialAppConfig>();
        queryWrapper.eq("app_code", appCode);
        queryWrapper.eq("corp_id", corpId);
        queryWrapper.eq("deleted_flag", DATA_DELETE_FLAG_NOT);
        return systemSocialAppConfigMapper.selectOne(queryWrapper);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemSocialAppConfigDTO selectSysSocialAppConfigByCropId(String cropId, String source, String appCode) {
        LambdaPrivernalWrapper<SystemSocialAppConfig> queryWrapper = new LambdaPrivernalWrapper<SystemSocialAppConfig>();
        if (StringUtils.isNotEmpty(source)) {
            queryWrapper.eq(SystemSocialAppConfig::getSource, source);
        }
        if (StringUtils.isNotEmpty(appCode)) {
            queryWrapper.eq(SystemSocialAppConfig::getAppCode, appCode);
        }
        if (StringUtils.isNotEmpty(cropId)) {
            queryWrapper.eq(SystemSocialAppConfig::getCorpId, cropId);
        }

        SystemSocialAppConfig systemSocialAppConfig = systemSocialAppConfigMapper.selectOne(queryWrapper);
        SystemSocialAppConfigDTO systemSocialAppConfigDTO = new SystemSocialAppConfigDTO();
        if (systemSocialAppConfig == null) {
            return systemSocialAppConfigDTO;
        }
        BeanUtils.copyProperties(systemSocialAppConfig, systemSocialAppConfigDTO);

        return systemSocialAppConfigDTO;
    }

    @Transactional(readOnly = true)
    @Override
    public SystemSocialAppConfigDTO selectSysSocialAppConfigByCodeAndSourceAndOrganizationId(String appCode, String source, String agentId, String cropId, String organizationId) {
        LambdaPrivernalWrapper<SystemSocialAppConfig> queryWrapper = new LambdaPrivernalWrapper<SystemSocialAppConfig>();
        if (StringUtils.isNotEmpty(source)) {
            queryWrapper.eq(SystemSocialAppConfig::getSource, source);
        }
        if (StringUtils.isNotEmpty(appCode)) {
            queryWrapper.eq(SystemSocialAppConfig::getAppCode, appCode);
        }
        if (StringUtils.isNotEmpty(cropId)) {
            queryWrapper.eq(SystemSocialAppConfig::getCorpId, cropId);
        }
        if (StringUtils.isNotEmpty(agentId)) {
            queryWrapper.eq(SystemSocialAppConfig::getAgentId, agentId);
        }
        if (StringUtils.isNotEmpty(organizationId)) {
            queryWrapper.eq(SystemSocialAppConfig::getOrganizationId, organizationId);
        }

        SystemSocialAppConfig systemSocialAppConfig = systemSocialAppConfigMapper.selectOne(queryWrapper);
        SystemSocialAppConfigDTO systemSocialAppConfigDTO = new SystemSocialAppConfigDTO();
        if (systemSocialAppConfig == null) {
            return systemSocialAppConfigDTO;
        }
        BeanUtils.copyProperties(systemSocialAppConfig, systemSocialAppConfigDTO);

        return systemSocialAppConfigDTO;
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemSocialAppConfigDTO> selectSysSocialAppConfigListByCodeAndSource(String appCode, String source) {
        LambdaPrivernalWrapper<SystemSocialAppConfig> queryWrapper = new LambdaPrivernalWrapper<SystemSocialAppConfig>();
        queryWrapper.eq(SystemSocialAppConfig::getSource, source);
        queryWrapper.eq(SystemSocialAppConfig::getAppCode, appCode);

        List<SystemSocialAppConfig> systemSocialAppConfigList = systemSocialAppConfigMapper.selectList(queryWrapper);

        List<SystemSocialAppConfigDTO> systemSocialAppConfigDTOList = new ArrayList<>();

        if (systemSocialAppConfigList != null) {

            for (SystemSocialAppConfig systemSocialAppConfig : systemSocialAppConfigList) {
                SystemSocialAppConfigDTO systemSocialAppConfigDTO = new SystemSocialAppConfigDTO();
                BeanUtils.copyProperties(systemSocialAppConfig, systemSocialAppConfigDTO);
                systemSocialAppConfigDTOList.add(systemSocialAppConfigDTO);
            }
            return systemSocialAppConfigDTOList;
        }
        return systemSocialAppConfigDTOList;
    }
}
