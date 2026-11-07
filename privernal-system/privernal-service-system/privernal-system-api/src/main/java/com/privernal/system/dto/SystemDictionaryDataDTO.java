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
 * 字典数据对象 system_dictionary_data
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 字典数据对象 system_dictionary_data
 */
@Tag(name = "SystemDictionaryDataDTO", description = "字典数据对应的DTO")
@Data
public class SystemDictionaryDataDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 字典id
     */
    @Schema(description = "字典id")

    private String id;

    /**
     * 字典排序
     */
    @Schema(description = "字典排序")

    private Long dictSort;

    /**
     * 字典标签
     */
    @Schema(description = "字典标签")

    private String dictLabel;

    /**
     * 字典键值
     */
    @Schema(description = "字典键值")

    private String dictValue;

    /**
     * 字典类型
     */
    @Schema(description = "字典类型")

    private String dictType;

    /**
     * 样式属性（其他样式扩展）
     */
    @Schema(description = "样式属性（其他样式扩展）")

    private String cssClass;

    /**
     * 表格回显样式
     */
    @Schema(description = "表格回显样式")

    private String listClass;

    /**
     * 是否默认（Y是 N否）
     */
    @Schema(description = "是否默认（Y是 N否）")

    private String isDefault;

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

    /**
     * 类型的名称
     */
    private String dictTypeName;
}
