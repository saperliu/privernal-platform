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

import com.privernal.system.domain.SystemUserDepartment;
import com.privernal.system.dto.SystemUserDepartmentDTO;
import com.privernal.system.filter.SystemUserDepartmentFilterDTO;

import java.util.List;

/**
 * 用户兼职部门的Service接口
 *
 * @author saperliu
 * @date 2023-10-10
 * @Description 用户兼职部门的Service接口
 */
public interface ISystemUserDepartmentService {
    /**
     * 查询用户兼职部门
     *
     * @param id 用户兼职部门主键
     * @return 用户兼职部门
     */
    public SystemUserDepartment selectSystemUserDepartmentById(String id);

    /**
     * 查询用户兼职部门列表
     *
     * @param systemUserDepartment 用户兼职部门
     * @return 用户兼职部门集合
     */
    public List<SystemUserDepartmentDTO> selectSystemUserDepartmentList(SystemUserDepartmentFilterDTO systemUserDepartment);

    /**
     * 新增用户兼职部门
     *
     * @param systemUserDepartment 用户兼职部门
     * @return 结果
     */
    public int insertSystemUserDepartment(SystemUserDepartment systemUserDepartment);

    /**
     * 修改用户兼职部门
     *
     * @param systemUserDepartment 用户兼职部门
     * @return 结果
     */
    public int updateSystemUserDepartment(SystemUserDepartment systemUserDepartment);

    /**
     * 批量删除用户兼职部门
     *
     * @param ids 需要删除的用户兼职部门主键集合
     * @return 结果
     */
    public int deleteSystemUserDepartmentByIds(String[] ids);

    /**
     * 删除用户兼职部门信息
     *
     * @param id 用户兼职部门主键
     * @return 结果
     */
    public int deleteSystemUserDepartmentById(String id);

}
