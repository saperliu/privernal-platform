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
 * 角色组与角色对象 system_role_group_role
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色组与角色对象 system_role_group_role
 */
@Tag(name = "SystemRoleGroupRoleDTO", description = "角色组与角色对应的DTO")
@Data
public class SystemRoleGroupRoleDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 组ID
     */
    @Schema(description = "组ID")

    private String groupId;

    /**
     * 角色ID
     */
    @Schema(description = "角色ID")

    private String roleId;

}
