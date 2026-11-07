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

package com.privernal.common.web.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serializable;

/**
 * 字典数据对象 dictionary_data
 *
 * @author saperliu
 * @date 2023-10-22
 * @Description 字典数据对象 dictionary_data
 */
@Tag(name = "DictionaryDataDTO", description = "字典数据对应的DTO")
@Data
public class DictionaryDataDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 字典id
     */
    @Schema(description = "字典id")
    private String id;


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

}
