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
 * 组织授权的应用对象 system_organization_app
 *
 * @author liuguanglu
 * @date 2024-03-05
 * @Description 组织授权的应用对象 system_organization_app
 */
@Tag(name = "SystemOrganizationAppDetailDTO", description = "组织授权的应用对应的详情DTO")
@Data
public class SystemOrganizationAppDetailDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 应用的ID
     */
    @Schema(description = "应用的ID")

    private String appId;

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

}
