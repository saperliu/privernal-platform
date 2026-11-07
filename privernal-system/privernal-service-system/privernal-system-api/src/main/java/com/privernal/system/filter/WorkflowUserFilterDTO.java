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

import java.util.List;

@Tag(name = "WorkflowUserFilterDTO", description = "工作流查询用户的条件")
@Data
public class WorkflowUserFilterDTO extends FilterDTO {

    /**
     * 所有用户ID
     */
    @Schema(description = "用户ID集合")
    private List<String> userList;

    /**
     * 所有部门的ID
     */
    @Schema(description = "部门ID集合")
    private List<String> deptList;

    /**
     * 所有角色的ID
     */
    @Schema(description = "角色ID集合")
    private List<String> roleList;

    /**
     * 所有用户组的ID
     */
    @Schema(description = "用户组ID集合")
    private List<String> userGroupList;

    /**
     * 特定部门
     */
    private String departmentId;
}
