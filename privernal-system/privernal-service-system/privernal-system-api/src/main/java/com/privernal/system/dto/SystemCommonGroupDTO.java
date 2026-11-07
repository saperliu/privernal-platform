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

import java.util.List;

/**
 * 公共组[用户或角色]对象 system_common_group
 *
 * @author liuguanglu
 * @date 2024-03-05
 * @Description 公共组[用户或角色]对象 system_common_group
 */
@Tag(name = "SystemCommonGroupDTO", description = "公共组[用户或角色]对应的DTO")
@Data
public class SystemCommonGroupDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 组名
     */
    @Schema(description = "组名")

    private String name;

    /**
     * 组编码
     */
    @Schema(description = "组编码")

    private String code;

    /**
     * 模块类型
     */
    @Schema(description = "模块类型")

    private String moduleType;

    /**
     * 描述
     */
    @Schema(description = "描述")

    private String description;

    /**
     * 类型
     */
    @Schema(description = "类型")

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

    @Schema(description = "菜单")
    private List<SystemCommonGroupMenuDTO> menuList;
}
