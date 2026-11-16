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

package com.privernal.system.filter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 权限台账查询条件（R1-8 权限治理②）
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：权限台账查询条件
 */
@Schema(name = "PermissionMatrixFilterDTO", description = "权限台账查询条件")
@Data
public class PermissionMatrixFilterDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "组织ID（非平台管理员由后端强制为当前组织）")
    private String organizationId;

    @Schema(description = "用户ID（可选，查单人台账）")
    private String userId;

    @Schema(description = "角色ID（可选）")
    private String roleId;
}
