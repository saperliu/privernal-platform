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

import com.privernal.common.web.domain.FilterDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * 用户兼职部门对象 system_user_department
 *
 * @author saperliu
 * @date 2023-10-10
 * @Description 用户兼职部门的过滤器
 */
@Tag(name = "SystemUserDepartmentFilterDTO", description = "用户兼职部门对应的查询过滤器")
@Data
public class SystemUserDepartmentFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    private String userId;


    /**
     * 组织ID;此数据所属组织
     */
    @Schema(description = "组织ID;此数据所属组织")
    private String organizationId;


    /**
     * 部门ID;所属部门
     */
    @Schema(description = "部门ID;所属部门")
    private String departmentId;


    /**
     * 是否主部门(0 否，1是)，目前主部门放在了user表里
     */
    @Schema(description = "是否主部门(0 否，1是)，目前主部门放在了user表里")
    private Long isMain;


}
