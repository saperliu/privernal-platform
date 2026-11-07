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
import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemAppInfo;
import com.privernal.system.dto.SystemAppInfoDTO;
import com.privernal.system.filter.SystemAppInfoFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 应用信息Mapper接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用信息Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:appInfo")
public interface SystemAppInfoMapper extends PrivernalMapper<SystemAppInfo> {
    /**
     * 查询应用信息列表
     *
     * @param systemAppInfo 应用信息
     * @return 应用信息集合
     */
    @PrivernalDataScope(mainAlias = "appInfo", permission = "system:appInfo:list")
    public List<SystemAppInfoDTO> selectSystemAppInfoList(SystemAppInfoFilterDTO systemAppInfo);


    /**
     * 查询应用信息列表
     *
     * @param organizationId 组织ID
     * @return 应用信息集合
     */
    @IgnoreDataScope
    public List<SystemAppInfoDTO> selectSystemAppInfoListByOrganizationId(@Param("organizationId") String organizationId);


    /**
     * 根据组织ID和用户ID查询应用信息列表
     * 通过用户的角色进行过滤
     *
     * @param organizationId 组织
     * @param userId         用户
     * @return
     */
    @IgnoreDataScope
    public List<SystemAppInfoDTO> selectSystemAppInfoListByOrganizationIdAndUserId(@Param("organizationId") String organizationId, @Param("userId") String userId);

    /**
     * 统计组织内非系统应用（type != 'SYSTEM'，即个人/业务创建的应用）数量
     *
     * @param organizationId 组织ID
     * @return 应用数量
     */
    @IgnoreDataScope
    public Long selectCustomAppNumByOrganizationId(@Param("organizationId") String organizationId);
}
