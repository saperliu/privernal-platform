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
 * <p>
 * 角色用户-对应的组织权限
 *
 * @author saperliu
 * @date 2022-04-10
 * @Description 角色用户-对应的组织权限
 */
@Tag(name = "SystemRoleDataScopeDTO", description = "角色用户-对应的组织权限对应的DTO")
@Data
public class SystemRoleDataScopeDTO extends BaseDTO {

    private static final long serialVersionUID = 1L;

    /**
     * 角色ID
     */
    @Schema(description = "角色ID")
    private String roleId;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "应用ID")
    private String appId;

    /**
     * 资源ID
     */
    @Schema(description = "资源ID")
    private String resourceId;

    /**
     * 权限标识
     */
    @Schema(description = "权限标识")
    private String permission;

    /**
     * 组织ID;所属组织
     */
    @Schema(description = "组织ID;所属组织")
    private String organizationId;

    /**
     * 数据权限（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）
     */
    @Schema(description = "数据权限;数据范围（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）")
    private Long dataScope;

    /**
     * 部门ID;服务的部门
     */
    @Schema(description = "部门ID;服务的部门")
    private String departmentId;
}
