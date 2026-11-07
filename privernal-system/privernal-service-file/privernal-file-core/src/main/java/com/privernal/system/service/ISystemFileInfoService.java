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

import com.privernal.system.domain.SystemFileInfo;
import com.privernal.system.dto.SystemFileInfoDTO;
import com.privernal.system.filter.SystemFileInfoFilterDTO;

import java.util.List;

/**
 * 文件信息的Service接口
 *
 * @author saperliu
 * @date 2023-10-16
 * @Description 文件信息的Service接口
 */
public interface ISystemFileInfoService {
    /**
     * 查询文件信息
     *
     * @param id 文件信息主键
     * @return 文件信息
     */
    public SystemFileInfo selectSystemFileInfoById(String id);

    /**
     * 查询文件信息列表
     *
     * @param systemFileInfo 文件信息
     * @return 文件信息集合
     */
    public List<SystemFileInfoDTO> selectSystemFileInfoList(SystemFileInfoFilterDTO systemFileInfo);

    /**
     * 新增文件信息
     *
     * @param systemFileInfo 文件信息
     * @return 结果
     */
    public int insertSystemFileInfo(SystemFileInfo systemFileInfo);

    /**
     * 修改文件信息
     *
     * @param systemFileInfo 文件信息
     * @return 结果
     */
    public int updateSystemFileInfo(SystemFileInfo systemFileInfo);

    /**
     * 批量删除文件信息
     *
     * @param ids 需要删除的文件信息主键集合
     * @return 结果
     */
    public int deleteSystemFileInfoByIds(String[] ids);

    /**
     * 删除文件信息信息
     *
     * @param id 文件信息主键
     * @return 结果
     */
    public int deleteSystemFileInfoById(String id);

}
