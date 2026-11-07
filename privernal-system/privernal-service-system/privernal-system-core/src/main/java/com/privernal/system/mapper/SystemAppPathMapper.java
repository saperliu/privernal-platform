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
import com.privernal.system.domain.SystemAppPath;
import com.privernal.system.dto.SystemAppPathDTO;
import com.privernal.system.filter.SystemAppPathFilterDTO;

import java.util.List;

/**
 * 应用可以访问的路径Mapper接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用可以访问的路径Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:appPath")
public interface SystemAppPathMapper extends PrivernalMapper<SystemAppPath> {
    /**
     * 查询应用可以访问的路径列表
     *
     * @param systemAppPath 应用可以访问的路径
     * @return 应用可以访问的路径集合
     */
    @PrivernalDataScope(mainAlias = "appPath", permission = "system:appPath:list")
    public List<SystemAppPathDTO> selectSystemAppPathList(SystemAppPathFilterDTO systemAppPath);

}
