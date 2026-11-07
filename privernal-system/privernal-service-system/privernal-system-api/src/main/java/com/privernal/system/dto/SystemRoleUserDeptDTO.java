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

package com.privernal.system.dto;

import com.privernal.common.web.domain.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.util.List;

/**
 * 角色用户与部门关系对象 system_role_user_dept
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色用户与部门关系对象 system_role_user_dept
 */
@Tag(name = "SystemRoleUserDeptDTO", description = "角色用户与部门关系对应的DTO")
@Data
public class SystemRoleUserDeptDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 角色用户表的ID;角色用户表的ID
     */
    @Schema(description = "角色用户表的ID;角色用户表的ID")

    private String roleUserId;

    /**
     * 组织ID;所属组织
     */
    @Schema(description = "组织ID;所属组织")

    private String organizationId;

    /**
     * 手动授权【1】/自动授权【2】
     *  默认手动授权
     */
    @Schema(description = "手动授权【1】/自动授权【2】")
    private int authWay = 1;

    /**
     * 部门ID;服务的部门
     */
    @Schema(description = "部门ID;服务的部门")
    private String departmentId;

    @Schema(description = "部门名称")
    private String deptName;

    private String status;

    @Schema(description = "部门列表")
    private transient List<SystemDepartmentDTO> departmentDTOList;

    @Schema(description = "新增的部门列表")
    private transient List<SystemDepartmentDTO> addDepartmentDTOList;

    @Schema(description = "删除的部门列表")
    private transient List<SystemDepartmentDTO> delDepartmentDTOList;
}
