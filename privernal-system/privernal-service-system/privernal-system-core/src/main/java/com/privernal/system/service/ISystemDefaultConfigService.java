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

import com.privernal.system.domain.SystemDefaultConfig;
import com.privernal.system.dto.AppSystemConfigDTO;
import com.privernal.system.filter.SystemDefaultConfigFilterDTO;

import java.util.List;

/**
 * 系统配置的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 系统配置的Service接口
 */
public interface ISystemDefaultConfigService {
    /**
     * 查询系统配置
     *
     * @param id 系统配置主键
     * @return 系统配置
     */
    public SystemDefaultConfig selectSystemDefaultConfigById(String id);

    /**
     * 查询系统配置列表
     *
     * @param systemDefaultConfig 系统配置
     * @return 系统配置集合
     */
    public List<SystemDefaultConfig> selectSystemDefaultConfigList(SystemDefaultConfigFilterDTO systemDefaultConfig);

    /**
     * 新增系统配置
     *
     * @param systemDefaultConfig 系统配置
     * @return 结果
     */
    public int insertSystemDefaultConfig(SystemDefaultConfig systemDefaultConfig);

    /**
     * /**
     * 修改系统配置
     *
     * @param systemDefaultConfig 系统配置
     * @return 结果
     */
    public int updateSystemDefaultConfig(SystemDefaultConfig systemDefaultConfig);

    /**
     * 批量删除系统配置
     *
     * @param ids 需要删除的系统配置主键集合
     * @return 结果
     */
    public int deleteSystemDefaultConfigByIds(String[] ids);

    /**
     * 删除系统配置信息
     *
     * @param id 系统配置主键
     * @return 结果
     */
    public int deleteSystemDefaultConfigById(String id);

    /**
     * 查询系统配置列表
     *
     * @param key 系统配置的KEY
     * @return 系统配置集合
     */
    public List<SystemDefaultConfig> selectSystemDefaultConfigList(String key);

    /**
     * 查询APP的网络系统配置
     *
     * @return 系统配置集合
     */
    public List<SystemDefaultConfig<AppSystemConfigDTO>> selectNetConfigList();
}
