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

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 组织对象 system_organization
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 组织对象 system_organization
 */
@Tag(name = "SystemOrganizationMenuDTO", description = "组织的菜单的DTO")
@Data
public class SystemOrganizationMenuCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 组织ID
     */
    @Schema(description = "组织ID")
    private String organizationId;

    /**
     * 菜单列表
     */
    @Schema(description = "菜单列表")
    private List<SystemMenuDTO> menuDTOList;


    private String appId;
}
