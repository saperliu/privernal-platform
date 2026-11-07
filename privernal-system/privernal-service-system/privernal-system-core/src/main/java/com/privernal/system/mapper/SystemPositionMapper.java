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
import com.privernal.system.domain.SystemPosition;
import com.privernal.system.dto.SystemUserPositionDTO;
import com.privernal.system.filter.SystemPositionFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 岗位Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 岗位Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:position")
public interface SystemPositionMapper extends PrivernalMapper<SystemPosition> {
    /**
     * 查询岗位列表
     *
     * @param systemPosition 岗位
     * @return 岗位集合
     */
    @PrivernalDataScope(mainAlias = "position", permission = "system:position:list")
    public List<SystemPosition> selectSystemPositionList(SystemPositionFilterDTO systemPosition);


    /**
     * 查询岗位里的用户
     *
     * @param positionId 岗位ID
     * @return 用户集合
     */
    @PrivernalDataScope(mainAlias = "position", permission = "system:position:list", ignoreDepartment = true)
    public List<SystemUserPositionDTO> selectSystemUserPositionList(@Param("positionId") String positionId, @Param("nickName") String nickName);

}
