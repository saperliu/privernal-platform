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

/**
 * 角色对象 system_role
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色对象 system_role
 */
@Tag(name = "SystemRoleDTO", description = "角色对应的DTO")
@Data
public class SystemRoleDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 数据权限（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）
     */
    @Schema(description = "数据权限;数据范围（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限)")
    private Long dataScope;

    /**
     * 角色KEY
     */
    @Schema(description = "角色KEY")

    private String roleKey;

    /**
     * 状态;角色状态（0正常 1停用）
     */
    @Schema(description = "状态;角色状态（0正常 1停用）")

    private Long status;

    /**
     * 排序值
     */
    @Schema(description = "排序值")

    private Long orderNum;

    /**
     * 角色名称
     */
    @Schema(description = "角色名称")

    private String roleName;

    /**
     * 角色的系统类型 NORMAL 普通   SYSTEM  系统
     */
    @Schema(description = "角色的系统类型 NORMAL 普通   SYSTEM  系统")

    private String systemType;

    /**
     * 组织ID
     */
    @Schema(description = "组织ID")

    private String organizationId;

    /**
     * 角色分类ID
     */
    @Schema(description = "角色分类ID")

    private String classificationId;

    /**
     * 部门ID
     */
    @Schema(description = "部门ID")

    private String departmentId;

    @Schema(description = "部门名称")
    private String deptName;

}
