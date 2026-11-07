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
import com.privernal.system.domain.SystemDepartment;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.filter.SystemDepartmentFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 部门Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 部门Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:department", ignoreDepartment = true)
public interface SystemDepartmentMapper extends PrivernalMapper<SystemDepartment> {
    /**
     * 查询部门列表
     *
     * @param systemDepartment 部门
     * @return 部门集合
     */
    @PrivernalDataScope(mainAlias = "department", deptField = "id", permission = "system:department:list")
    public List<SystemDepartment> selectSystemDepartmentList(SystemDepartmentFilterDTO systemDepartment);


    /**
     * 查询部门
     * 工作流使用
     *
     * @param deptList 部门的ID集合
     * @return 部门列表
     */
    public List<SystemDepartmentDTO> selectSystemDepartmentListByIds(@Param("deptList") List<String> deptList);

    /**
     * 查询部门列表
     * 超级管理员，切换组织时使用,
     *
     * @param systemDepartment 部门
     * @return 部门集合
     */
    public List<SystemDepartment> selectDepartmentList(SystemDepartmentFilterDTO systemDepartment);


    /**
     * 查询部门树形数据
     * 显示部门树型结构时使用的数据
     *
     * @param systemDepartmentFilter 条件
     * @return 部门集合
     */
    public List<SystemDepartmentDTO> selectDepartmentByOrganizationId(SystemDepartmentFilterDTO systemDepartmentFilter);
}
