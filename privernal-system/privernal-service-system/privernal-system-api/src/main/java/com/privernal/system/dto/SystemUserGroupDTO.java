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
 * 用户组对象 system_user_group
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户组对象 system_user_group
 */
@Tag(name = "SystemUserGroupDTO", description = "用户组对应的DTO")
@Data
public class SystemUserGroupDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 组名
     */
    @Schema(description = "组名编码")

    private String groupCode;

    /**
     * 组名
     */
    @Schema(description = "组名")

    private String groupName;

    /**
     * 类型;SYSTEM 系统默认 CUSTOM 用户创建
     */
    @Schema(description = "类型;SYSTEM 系统默认 CUSTOM 用户创建")

    private String groupType;

    /**
     * 组织ID
     */
    @Schema(description = "组织ID")

    private String organizationId;

    /**
     * 部门ID
     */
    @Schema(description = "部门ID")

    private String departmentId;

    /**
     * 公共组的ID
     */
    private String commonGroupId;

    /**
     * 状态
     */
    private Long status;
}
