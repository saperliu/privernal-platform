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

@Tag(name = "WorkflowCodeFilterDTO", description = "工作流查询用户的条件")
@Data
public class WorkflowCodeFilterDTO extends FilterDTO {

    /**
     * 编码
     */
    @Schema(description = "编码")
    private String code;

    /**
     * 编码
     */
    @Schema(description = "多个编码")
    private List<String> codeList;

    /**
     * 特定部门
     */
    @Schema(description = "特定部门")
    private String departmentId;


    /**
     * 特定组织
     */
    @Schema(description = "特定组织")
    private String organizationId;
}
