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

import com.privernal.system.domain.SystemFileConfig;
import com.privernal.system.dto.SystemFileConfigDTO;
import com.privernal.system.filter.SystemFileConfigFilterDTO;

import java.util.List;

/**
 * 文件配置的Service接口
 *
 * @author saperliu
 * @date 2023-10-16
 * @Description 文件配置的Service接口
 */
public interface ISystemFileConfigService {
    /**
     * 查询文件配置
     *
     * @param id 文件配置主键
     * @return 文件配置
     */
    public SystemFileConfig selectSystemFileConfigById(String id);

    /**
     * 查询文件配置列表
     *
     * @param systemFileConfig 文件配置
     * @return 文件配置集合
     */
    public List<SystemFileConfigDTO> selectSystemFileConfigList(SystemFileConfigFilterDTO systemFileConfig);

    /**
     * 新增文件配置
     *
     * @param systemFileConfig 文件配置
     * @return 结果
     */
    public int insertSystemFileConfig(SystemFileConfig systemFileConfig);

    /**
     * 修改文件配置
     *
     * @param systemFileConfig 文件配置
     * @return 结果
     */
    public int updateSystemFileConfig(SystemFileConfig systemFileConfig);

    /**
     * 批量删除文件配置
     *
     * @param ids 需要删除的文件配置主键集合
     * @return 结果
     */
    public int deleteSystemFileConfigByIds(String[] ids);

    /**
     * 删除文件配置信息
     *
     * @param id 文件配置主键
     * @return 结果
     */
    public int deleteSystemFileConfigById(String id);

}
