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
 * 部门内联关系对象 system_dept_relation
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 部门内联关系对象 system_dept_relation
 */
@Tag(name = "SystemDeptRelationDTO", description = "部门内联关系对应的DTO")
@Data
public class SystemDeptRelationDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 部门ID;上级部门
     */
    @Schema(description = "部门ID;上级部门")

    private String parentId;

    /**
     * 部门ID;下级部门
     */
    @Schema(description = "部门ID;下级部门")

    private String deptId;

    /**
     * 层级;上与下级是第几层级:顶级是0 组织名称。部门级别都是多1开始
     */
    @Schema(description = "层级;上与下级是第几层级:顶级是0 组织名称。部门级别都是多1开始")

    private Long level;

}
