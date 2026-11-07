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
import com.privernal.system.domain.SystemCommonGroup;
import com.privernal.system.dto.SystemCommonGroupDTO;
import com.privernal.system.filter.SystemCommonGroupFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 公共组[用户或角色]Mapper接口
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 公共组[用户或角色]Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:commonGroup")
public interface SystemCommonGroupMapper extends PrivernalMapper<SystemCommonGroup> {
    /**
     * 查询公共组[用户或角色]列表
     *
     * @param systemCommonGroup 公共组[用户或角色]
     * @return 公共组[用户或角色]集合
     */
//    @PrivernalDataScope(mainAlias = "commonGroup", permission = "system:commonGroup:list")
    public List<SystemCommonGroupDTO> selectSystemCommonGroupList(SystemCommonGroupFilterDTO systemCommonGroup);

    /**
     * 根据组织id查询公共组[用户或角色]列表
     *
     * @param appId
     * @return 公共组集合
     */
    public List<SystemCommonGroupDTO> selectSystemCommonGroupListByAppId(@Param("appId") String appId);


}
