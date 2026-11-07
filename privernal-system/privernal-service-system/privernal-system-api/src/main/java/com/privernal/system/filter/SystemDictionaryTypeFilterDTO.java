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
 * 字典类型对象 system_dictionary_type
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 字典类型的过滤器
 */
@Tag(name = "SystemDictionaryTypeFilterDTO", description = "字典类型对应的查询过滤器")
@Data
public class SystemDictionaryTypeFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 字典主键
     */
    @Schema(description = "字典主键")
    private String id;


    /**
     * 字典名称
     */
    @Schema(description = "字典名称")
    private String dictName;


    /**
     * 字典类型
     */
    @Schema(description = "字典类型")
    private String dictType;


    /**
     * 状态（0正常 1停用）
     */
    @Schema(description = "状态（0正常 1停用）")
    private Long status;


    /**
     * 组织
     */
    @Schema(description = "组织")
    private String organizationId;


    /**
     * 部门
     */
    @Schema(description = "部门")
    private String departmentId;


}
