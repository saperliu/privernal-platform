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

package com.privernal.flow.mapper;

import com.privernal.common.datascope.annotation.IgnoreDataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户角色名查询（网关流转条件 HAS_ROLE / 发起人上下文变量用）。
 * system_role_user / system_role 与流程表同库，直接注解查询；
 * 与 FlowQueryMapper 同理豁免组织拦截器与部门数据权限。
 *
 * @author saperliu
 */
@IgnoreDataScope
@Mapper
public interface FlowRoleNameMapper {

    @Select("SELECT r.role_name FROM system_role_user ru "
            + "JOIN system_role r ON r.id = ru.role_id AND r.deleted_flag = 'N' "
            + "WHERE ru.deleted_flag = 'N' AND ru.user_id = #{userId} ORDER BY r.role_name")
    List<String> selectRoleNamesByUserId(@Param("userId") String userId);
}
