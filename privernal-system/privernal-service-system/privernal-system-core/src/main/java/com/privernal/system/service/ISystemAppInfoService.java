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

import com.privernal.system.domain.SystemAppInfo;
import com.privernal.system.dto.SystemAppInfoDTO;
import com.privernal.system.dto.SystemAppInfoDetailDTO;
import com.privernal.system.filter.SystemAppInfoFilterDTO;

import java.util.List;

/**
 * 应用信息的Service接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用信息的Service接口
 */
public interface ISystemAppInfoService {
    /**
     * 查询应用信息
     *
     * @param id 应用信息主键
     * @return 应用信息
     */
    public SystemAppInfo selectSystemAppInfoById(String id);

    /**
     * 查询应用信息
     *
     * @param id 应用信息主键
     * @return 应用信息
     */
    public SystemAppInfoDetailDTO selectSystemAppInfoDetailDTOById(String id);

    /**
     * 查询应用信息列表
     *
     * @param systemAppInfo 应用信息
     * @return 应用信息集合
     */
    public List<SystemAppInfoDTO> selectSystemAppInfoList(SystemAppInfoFilterDTO systemAppInfo);

    /**
     * 新增应用信息
     *
     * @param systemAppInfo 应用信息
     * @return 结果
     */
    public SystemAppInfo insertSystemAppInfo(SystemAppInfo systemAppInfo);

    /**
     * 修改应用信息
     *
     * @param systemAppInfo 应用信息
     * @return 结果
     */
    public SystemAppInfo updateSystemAppInfo(SystemAppInfo systemAppInfo);

    /**
     * 新增或修改应用信息
     *
     * @param systemAppInfo 应用信息
     * @return 结果
     */
    public SystemAppInfo saveSystemAppInfo(SystemAppInfoDTO systemAppInfo);

    /**
     * 批量删除应用信息
     *
     * @param ids 需要删除的应用信息主键集合
     * @return 结果
     */
    public int deleteSystemAppInfoByIds(String[] ids);

    /**
     * 删除应用信息信息
     *
     * @param id 应用信息主键
     * @return 结果
     */
    public int deleteSystemAppInfoById(String id);

    /**
     * 查询应用信息列表
     *
     * @param organizationId 组织ID
     * @param userId         用户ID
     * @return 应用信息集合
     */
    public List<SystemAppInfoDTO> selectSystemAppInfoListByUserId(String organizationId, String userId);

    /**
     * 统计组织内非系统应用（type != 'SYSTEM'，即个人/业务创建的应用）数量
     *
     * @param organizationId 组织ID
     * @return 应用数量
     */
    public Long selectCustomAppNumByOrganizationId(String organizationId);
}
