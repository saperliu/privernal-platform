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

import com.privernal.system.domain.SystemDeptRelation;
import com.privernal.system.filter.SystemDeptRelationFilterDTO;

import java.util.List;

/**
 * 部门内联关系的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 部门内联关系的Service接口
 */
public interface ISystemDeptRelationService {
    /**
     * 查询部门内联关系
     *
     * @param id 部门内联关系主键
     * @return 部门内联关系
     */
    public SystemDeptRelation selectSystemDeptRelationById(String id);

    /**
     * 查询部门内联关系列表
     *
     * @param systemDeptRelation 部门内联关系
     * @return 部门内联关系集合
     */
    public List<SystemDeptRelation> selectSystemDeptRelationList(SystemDeptRelationFilterDTO systemDeptRelation);

    /**
     * 新增部门内联关系
     *
     * @param systemDeptRelation 部门内联关系
     * @return 结果
     */
    public int insertSystemDeptRelation(SystemDeptRelation systemDeptRelation);

    /**
     * 修改部门内联关系
     *
     * @param systemDeptRelation 部门内联关系
     * @return 结果
     */
    public int updateSystemDeptRelation(SystemDeptRelation systemDeptRelation);

    /**
     * 批量删除部门内联关系
     *
     * @param ids 需要删除的部门内联关系主键集合
     * @return 结果
     */
    public int deleteSystemDeptRelationByIds(String[] ids);

    /**
     * 删除部门内联关系信息
     *
     * @param id 部门内联关系主键
     * @return 结果
     */
    public int deleteSystemDeptRelationById(String id);

}
