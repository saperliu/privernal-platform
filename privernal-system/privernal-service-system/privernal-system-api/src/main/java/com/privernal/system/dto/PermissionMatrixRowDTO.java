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
import lombok.Data;

import java.io.Serializable;

/**
 * 权限台账行（R1-8 权限治理②）：人 × 角色 × 应用 × 权限 × 数据范围
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：权限台账行
 */
@Schema(name = "PermissionMatrixRowDTO", description = "权限台账行（人×应用×数据范围）")
@Data
public class PermissionMatrixRowDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "登录名")
    private String loginName;

    @Schema(description = "昵称")
    private String nickName;

    @Schema(description = "角色ID")
    private String roleId;

    @Schema(description = "角色名")
    private String roleName;

    @Schema(description = "应用ID（空=平台菜单）")
    private String appId;

    @Schema(description = "应用名（空=平台菜单）")
    private String appName;

    @Schema(description = "权限标识（菜单/按钮 permission）")
    private String permission;

    @Schema(description = "数据范围（10仅本人 20本部门 30本部门及以下 40自定义 50全部）")
    private Long dataScope;

    @Schema(description = "授权有效期（空=永久）")
    private String expireTime;
}
