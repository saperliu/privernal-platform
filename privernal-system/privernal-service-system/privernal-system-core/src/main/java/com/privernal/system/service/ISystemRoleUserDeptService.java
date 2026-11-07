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

import com.privernal.system.domain.SystemRoleUserDept;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.dto.SystemRoleUserDeptDTO;
import com.privernal.system.filter.SystemRoleUserDeptFilterDTO;

import java.util.List;

/**
 * 角色用户与部门关系的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色用户与部门关系的Service接口
 */
public interface ISystemRoleUserDeptService {
    /**
     * 查询角色用户与部门关系
     *
     * @param id 角色用户与部门关系主键
     * @return 角色用户与部门关系
     */
    public SystemRoleUserDept selectSystemRoleUserDeptById(String id);

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
     * 新增角色用户与部门关系
     *
     * @param systemRoleUserDept 角色用户与部门关系
     * @return 结果
     */
    public int insertSystemRoleUserDept(SystemRoleUserDept systemRoleUserDept);

    /**
     * 新增角色用户与部门关系
     *
     * @param systemRoleUserDeptDTO 角色用户与部门关系
     * @return 结果
     */
    public int insertBatchSystemRoleUserDept(SystemRoleUserDeptDTO systemRoleUserDeptDTO);

    /**
     * 修改角色用户与部门关系
     *
     * @param systemRoleUserDept 角色用户与部门关系
     * @return 结果
     */
    public int updateSystemRoleUserDept(SystemRoleUserDept systemRoleUserDept);

    /**
     * 批量删除角色用户与部门关系
     *
     * @param ids 需要删除的角色用户与部门关系主键集合
     * @return 结果
     */
    public int deleteSystemRoleUserDeptByIds(String[] ids);

    /**
     * 删除角色用户与部门关系信息
     *
     * @param id 角色用户与部门关系主键
     * @return 结果
     */
    public int deleteSystemRoleUserDeptById(String id);

    /**
     * 查询用户拥有的部门，主部门及服务部门
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 部门集合
     */
    public List<SystemDepartmentDTO> selectSystemDeptByUserIdAndRoleId(String userId, String roleId);
}
