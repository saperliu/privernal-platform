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
 * @author saperliu
 * @date 2023-08-22
 * @Description 角色菜单数据权限
 */
@Tag(name = "SystemRoleMenuScopeDTO", description = "角色菜单数据权限")
@Data
public class SystemRoleMenuScopeDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 菜单主键
     */
    @Schema(description = "菜单主键")
    private String id;

    /**
     * 菜单名称
     */
    @Schema(description = "菜单名称")
    private String menuName;

    /**
     * 权限标识;权限标识
     */
    @Schema(description = "权限标识")
    private String permission;

    /**
     * 数据权限（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）
     */
    @Schema(name = "数据权限", description = "50=：全部数据权限,40=：自定数据权限,20=：本部门数据权限,30=：本部门及以下数据权限,10=: 本人数据权限")
    private Long dataScope;

    /**
     * 组织ID;所属组织的配置
     */
    private String organizationId;

    /**
     * 应用ID
     */
    @Schema(description = "应用ID")
    private String appId;

    @Schema(description = "菜单ID")
    private String menuId;

    @Schema(description = "操作标识")
    private String actionTag;
}
