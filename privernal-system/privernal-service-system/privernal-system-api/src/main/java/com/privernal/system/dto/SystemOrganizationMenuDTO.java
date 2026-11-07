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
 * 组织拥有的菜单对象 system_organization_menu
 *
 * @author saperliu
 * @date 2023-04-17
 * @Description 组织拥有的菜单对象 system_organization_menu
 */
@Tag(name = "SystemOrganizationMenuDTO", description = "组织拥有的菜单对应的DTO")
@Data
public class SystemOrganizationMenuDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 菜单名称
     */
    @Schema(description = "菜单名称")

    private String menuId;

    /**
     * 菜单状态;菜单状态（0正常 1停用）
     */
    @Schema(description = "菜单状态;菜单状态（0正常 1停用）")

    private Long status;

    /**
     * 组织ID
     */
    @Schema(description = "组织ID")

    private String organizationId;

    /**
     * 应用ID
     */
    @Schema(description = "应用ID")
    private String appId;
}
