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

package com.privernal.system.mapper;


import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemFileConfig;
import com.privernal.system.dto.SystemFileConfigDTO;
import com.privernal.system.filter.SystemFileConfigFilterDTO;

import java.util.List;

/**
 * 文件配置Mapper接口
 *
 * @author saperliu
 * @date 2023-10-16
 * @Description 文件配置Mapper接口
 */
@PrivernalDataScope(mainAlias = "fileConfig", permissionMain = "system:fileConfig")
public interface SystemFileConfigMapper extends PrivernalMapper<SystemFileConfig> {
    /**
     * 查询文件配置列表
     *
     * @param systemFileConfig 文件配置
     * @return 文件配置集合
     */
    @PrivernalDataScope(mainAlias = "fileConfig", permission = "system:fileConfig:list")
    public List<SystemFileConfigDTO> selectSystemFileConfigList(SystemFileConfigFilterDTO systemFileConfig);

}
