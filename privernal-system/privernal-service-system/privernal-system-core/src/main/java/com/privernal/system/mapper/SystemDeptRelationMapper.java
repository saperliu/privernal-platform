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
import com.privernal.system.domain.SystemDeptRelation;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.filter.SystemDeptRelationFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 部门内联关系Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 部门内联关系Mapper接口
 */
@IgnoreDataScope
public interface SystemDeptRelationMapper extends PrivernalMapper<SystemDeptRelation> {
    /**
     * 查询部门内联关系列表
     *
     * @param systemDeptRelation 部门内联关系
     * @return 部门内联关系集合
     */
    @PrivernalDataScope(mainAlias = "deptRelation", deptField = "dept_id", permission = "system:deptRelation:list")
    public List<SystemDeptRelation> selectSystemDeptRelationList(SystemDeptRelationFilterDTO systemDeptRelation);

    /**
     * 查询此部门的所有父部门
     *
     * @param id 部门的ID
     * @return 部门列表
     */
    public List<SystemDeptRelation> selectAllParents(@Param("id") String id);


    /**
     * 查询此部门的所有父部门
     *
     * @param id 部门的ID
     * @return 部门列表
     */
    public SystemDeptRelation selectUpParent(@Param("id") String id);

    /**
     * 查询此部门的所有子部门
     *
     * @param id 部门的ID
     * @return 部门列表
     */
    public List<SystemDeptRelation> selectAuthAllChildren(String id);


    /**
     * 查询此部门的所有父部门
     *
     * @param id 部门的ID
     * @return 部门列表
     */
    public List<SystemDepartmentDTO> selectAllParentList(@Param("id") String id);

}
