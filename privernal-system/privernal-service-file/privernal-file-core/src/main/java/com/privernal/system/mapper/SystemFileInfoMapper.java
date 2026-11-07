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
import com.privernal.system.domain.SystemFileInfo;
import com.privernal.system.dto.SystemFileInfoDTO;
import com.privernal.system.filter.SystemFileInfoFilterDTO;

import java.util.List;

/**
 * 文件信息Mapper接口
 *
 * @author saperliu
 * @date 2023-10-16
 * @Description 文件信息Mapper接口
 */
@PrivernalDataScope(mainAlias = "fileInfo", permissionMain = "system:fileInfo")
public interface SystemFileInfoMapper extends PrivernalMapper<SystemFileInfo> {
    /**
     * 查询文件信息列表
     *
     * @param systemFileInfo 文件信息
     * @return 文件信息集合
     */
    @PrivernalDataScope(mainAlias = "fileInfo", permission = "system:fileInfo:list")
    public List<SystemFileInfoDTO> selectSystemFileInfoList(SystemFileInfoFilterDTO systemFileInfo);

}
