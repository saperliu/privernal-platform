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

package com.privernal.system.service;

import com.privernal.system.domain.SystemSocialAppConfig;
import com.privernal.system.dto.SystemSocialAppConfigDTO;
import com.privernal.system.filter.SystemSocialAppConfigFilterDTO;

import java.util.List;

/**
 * 第三方应用配置(小程序)的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 第三方应用配置(小程序)的Service接口
 */
public interface ISystemSocialAppConfigService {
    /**
     * 查询第三方应用配置(小程序)
     *
     * @param id 第三方应用配置(小程序)主键
     * @return 第三方应用配置(小程序)
     */
    public SystemSocialAppConfig selectSystemSocialAppConfigById(String id);

    /**
     * 查询第三方应用配置(小程序)列表
     *
     * @param systemSocialAppConfig 第三方应用配置(小程序)
     * @return 第三方应用配置(小程序)集合
     */
    public List<SystemSocialAppConfig> selectSystemSocialAppConfigList(SystemSocialAppConfigFilterDTO systemSocialAppConfig);

    /**
     * 新增第三方应用配置(小程序)
     *
     * @param systemSocialAppConfig 第三方应用配置(小程序)
     * @return 结果
     */
    public int insertSystemSocialAppConfig(SystemSocialAppConfig systemSocialAppConfig);

    /**
     * 修改第三方应用配置(小程序)
     *
     * @param systemSocialAppConfig 第三方应用配置(小程序)
     * @return 结果
     */
    public int updateSystemSocialAppConfig(SystemSocialAppConfig systemSocialAppConfig);

    /**
     * 批量删除第三方应用配置(小程序)
     *
     * @param ids 需要删除的第三方应用配置(小程序)主键集合
     * @return 结果
     */
    public int deleteSystemSocialAppConfigByIds(String[] ids);

    /**
     * 删除第三方应用配置(小程序)信息
     *
     * @param id 第三方应用配置(小程序)主键
     * @return 结果
     */
    public int deleteSystemSocialAppConfigById(String id);

    /**
     * 查询第三方应用配置(小程序)
     *
     * @param appCode 第三方应用配置的code
     * @return 第三方应用配置(小程序)
     */
    public SystemSocialAppConfig selectSysSocialAppConfigByCode(String appCode);

    /**
     * 查询第三方应用配置(小程序)
     *
     * @param appCode        第三方应用配置的code
     * @param organizationId 组织ID
     * @return 第三方应用配置(小程序)
     */
    public SystemSocialAppConfig selectSysSocialAppConfigByCodeAndOrganizationId(String appCode, String organizationId);

    /**
     * 查询第三方应用配置(小程序)
     *
     * @param appCode        第三方应用配置的code
     * @param source         来源
     * @param organizationId 组织ID
     * @return 第三方应用配置(小程序)
     */
    public SystemSocialAppConfig selectSysSocialAppConfigByCodeAndSourceAndOrganizationId(String appCode, String source, String organizationId);

    /**
     * 查询第三方应用配置(小程序)
     *
     * @param appCode 第三方应用配置的code
     * @param cropId  组织ID
     * @return 第三方应用配置(小程序)
     */
    public SystemSocialAppConfig selectSysSocialAppConfigByCodeAndCropId(String appCode, String cropId);

    /**
     * 查询第三方应用配置(小程序)
     *
     * @param appCode 第三方应用配置的code
     * @param cropId  组织ID
     * @return 第三方应用配置(小程序)
     */
    public SystemSocialAppConfigDTO selectSysSocialAppConfigByCropId(String cropId, String source, String appCode);

    /**
     * 查询第三方应用配置(小程序)
     *
     * @param appCode        第三方应用配置的code
     * @param source         来源
     * @param agentId        应用的ID
     * @param cropId         第三方企业ID
     * @param organizationId 组织ID
     * @return 第三方应用配置(小程序)
     */
    public SystemSocialAppConfigDTO selectSysSocialAppConfigByCodeAndSourceAndOrganizationId(String appCode, String source, String agentId, String cropId, String organizationId);

    /**
     * 查询第三方应用配置(小程序)
     *
     * @param appCode 第三方应用配置的code
     * @param source  来源
     * @return 第三方应用配置(小程序)
     */
    public List<SystemSocialAppConfigDTO> selectSysSocialAppConfigListByCodeAndSource( String appCode, String source);

}
