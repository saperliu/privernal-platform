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

import com.privernal.system.domain.SystemAppPath;
import com.privernal.system.dto.SystemAppPathDTO;
import com.privernal.system.dto.SystemAppPathDetailDTO;
import com.privernal.system.filter.SystemAppPathFilterDTO;

import java.util.List;

/**
 * 应用可以访问的路径的Service接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用可以访问的路径的Service接口
 */
public interface ISystemAppPathService {
    /**
     * 查询应用可以访问的路径
     *
     * @param id 应用可以访问的路径主键
     * @return 应用可以访问的路径
     */
    public SystemAppPath selectSystemAppPathById(String id);

    /**
     * 查询应用可以访问的路径
     *
     * @param id 应用可以访问的路径主键
     * @return 应用可以访问的路径
     */
    public SystemAppPathDetailDTO selectSystemAppPathDetailDTOById(String id);

    /**
     * 查询应用可以访问的路径列表
     *
     * @param systemAppPath 应用可以访问的路径
     * @return 应用可以访问的路径集合
     */
    public List<SystemAppPathDTO> selectSystemAppPathList(SystemAppPathFilterDTO systemAppPath);

    /**
     * 新增应用可以访问的路径
     *
     * @param systemAppPath 应用可以访问的路径
     * @return 结果
     */
    public SystemAppPath insertSystemAppPath(SystemAppPath systemAppPath);

    /**
     * 修改应用可以访问的路径
     *
     * @param systemAppPath 应用可以访问的路径
     * @return 结果
     */
    public SystemAppPath updateSystemAppPath(SystemAppPath systemAppPath);

    /**
     * 批量删除应用可以访问的路径
     *
     * @param ids 需要删除的应用可以访问的路径主键集合
     * @return 结果
     */
    public int deleteSystemAppPathByIds(String[] ids);

    /**
     * 删除应用可以访问的路径信息
     *
     * @param id 应用可以访问的路径主键
     * @return 结果
     */
    public int deleteSystemAppPathById(String id);

}
