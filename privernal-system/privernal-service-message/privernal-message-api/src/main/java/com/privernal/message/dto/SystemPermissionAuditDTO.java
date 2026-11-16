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

package com.privernal.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限审计报表行（R1-8 权限治理②）：system_log_operation 中权限相关变更的富化视图
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：权限审计报表行
 */
@Schema(name = "SystemPermissionAuditDTO", description = "权限审计报表行")
@Data
@EqualsAndHashCode(callSuper = true)
public class SystemPermissionAuditDTO extends SystemLogOperationDTO {
    private static final long serialVersionUID = 1L;

    @Schema(description = "动作归类（授权/回收/变更/停用/移交/代理/角色管理/用户管理）")
    private String action;

    @Schema(description = "对象摘要（解析参数后的可读描述）")
    private String targetSummary;

    @Schema(description = "涉及角色（ID解析为角色名）")
    private String roleNames;

    @Schema(description = "涉及用户（ID解析为昵称(loginName)）")
    private String userNames;
}
