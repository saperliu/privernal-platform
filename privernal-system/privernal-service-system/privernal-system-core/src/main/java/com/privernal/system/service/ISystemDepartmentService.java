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

import com.privernal.system.domain.SystemDepartment;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.filter.SystemDepartmentFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 部门的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 部门的Service接口
 */
public interface ISystemDepartmentService {
    /**
     * 查询部门
     *
     * @param id 部门主键
     * @return 部门
     */
    public SystemDepartment selectSystemDepartmentById(String id);

    /**
     * 查询部门列表
     *
     * @param systemDepartment 部门
     * @return 部门集合
     */
    public List<SystemDepartment> selectSystemDepartmentList(SystemDepartmentFilterDTO systemDepartment);

    /**
     * 新增部门
     *
     * @param systemDepartment 部门
     * @return 结果
     */
    public int insertSystemDepartment(SystemDepartment systemDepartment);

    /**
     * 修改部门
     *
     * @param systemDepartment 部门
     * @return 结果
     */
    public int updateSystemDepartment(SystemDepartment systemDepartment);

    /**
     * 批量删除部门
     *
     * @param ids 需要删除的部门主键集合
     * @return 结果
     */
    public int deleteSystemDepartmentByIds(String[] ids);

    /**
     * 删除部门信息
     *
     * @param id 部门主键
     * @return 结果
     */
    public int deleteSystemDepartmentById(String id);

    /**
     * 查询部门列表
     * 工作流查询部门
     *
     * @param deptList 部门ID集合
     * @return 部门集合
     */
    public List<SystemDepartmentDTO> selectSystemDepartmentList(List<String> deptList);

    /**
     * 查询部门列表
     * 超级管理员，切换组织时使用
     *
     * @param systemDepartment 部门
     * @return 部门集合
     */
    public List<SystemDepartment> selectDepartmentList(SystemDepartmentFilterDTO systemDepartment);

    /**
     * 查询部门树形数据
     * 显示部门树型结构时使用的数据
     *
     * @param systemDepartmentFilter 过滤条件
     * @return 部门集合
     */
    public List<SystemDepartmentDTO> selectDepartmentByOrganizationId(SystemDepartmentFilterDTO systemDepartmentFilter);

    /**
     * 查询部门的所有上级问题
     *
     * @param departmentId 部门ID
     * @return 部门集合
     */
    public List<SystemDepartmentDTO> selectSystemParentDeptById(String departmentId);

}
