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
import com.privernal.system.domain.SystemDefaultConfig;
import com.privernal.system.dto.AppSystemConfigDTO;
import com.privernal.system.filter.SystemDefaultConfigFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 系统配置Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:defaultConfig", ignoreDepartment = true)
public interface SystemDefaultConfigMapper extends PrivernalMapper<SystemDefaultConfig> {
    /**
     * 查询系统配置列表
     *
     * @param systemDefaultConfig 系统配置
     * @return 系统配置集合
     */
    @PrivernalDataScope(mainAlias = "defaultConfig", permission = "system:defaultConfig:list")
    public List<SystemDefaultConfig> selectSystemDefaultConfigList(SystemDefaultConfigFilterDTO systemDefaultConfig);

    /**
     * 查询配置信息
     *
     * @return
     */
    public List<SystemDefaultConfig> selectConfigListByKey(@Param("key") String key);

    /**
     * 查询APP的网址，不要权限，不要限制
     *
     * @return
     */
    public List<SystemDefaultConfig<AppSystemConfigDTO>> selectConfigNetAddressList();
}
