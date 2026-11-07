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

import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemRoleUserDept;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.dto.SystemRoleUserDeptDTO;
import com.privernal.system.filter.SystemRoleUserDeptFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色用户与部门关系Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 角色用户与部门关系Mapper接口
 */
@IgnoreDataScope
public interface SystemRoleUserDeptMapper extends PrivernalMapper<SystemRoleUserDept> {
    /**
     * 查询角色用户与部门关系列表
     *
     * @param systemRoleUserDept 角色用户与部门关系
     * @return 角色用户与部门关系集合
     */
    public List<SystemRoleUserDept> selectSystemRoleUserDeptList(SystemRoleUserDeptFilterDTO systemRoleUserDept);

    /**
     * 查询角色用户与部门关系列表
     *
     * @param systemRoleUserDept 角色用户与部门关系
     * @return 角色用户与部门关系集合
     */

    public List<SystemRoleUserDeptDTO> selectSystemRoleUserDeptDTOList(SystemRoleUserDeptFilterDTO systemRoleUserDept);

    /**
     * 查询用户拥有的角色的单独授权的部门数据
     *
     * @param userId 用户ID
     * @return 用户集合
     */
    public List<SystemRoleUserDept> selectSystemRoleUserDeptByRoleUserId(@Param("userId") String userId);

    /**
     * 查询用户拥有的角色的单独授权的部门数据
     *
     * @param userId 用户ID
     * @param deptId 部门ID
     * @return 用户集合
     */
    public SystemRoleUserDept selectSystemRoleUserDeptByRoleUserIdAndDeptId(@Param("userId") String userId, @Param("deptId") String deptId);

    /**
     * 查询用户拥有的部门，主部门及服务部门
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 部门集合
     */
    public List<SystemDepartmentDTO> selectSystemDeptByUserIdAndRoleId(@Param("userId") String userId, @Param("roleId") String roleId);


    /**
     * 查询此部门关联的人员
     *
     * @param deptId 部门ID
     * @return 集合
     */
    public List<SystemRoleUserDept> selectSystemRoleUserDeptByDeptId(@Param("deptId") String deptId);
}
