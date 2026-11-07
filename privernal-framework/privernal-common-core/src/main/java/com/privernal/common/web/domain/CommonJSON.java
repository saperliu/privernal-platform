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
import java.util.List;

/**
 * 公共的JSON返回类，
 * MYSQL存储的json数据的字典类型
 *
 * @author saperliu
 */
@Data
@Tag(name = "公共的JSON返回类", description = "统一返回数据的封装")
public class CommonJSON<T> implements Serializable {

    /**
     * 选择的数据，单条
     */
    @Schema(description = "选择的数据，单条")
    private String selected;
    /**
     * 选择的数据多条，逗号分隔
     */
    @Schema(description = "选择的数据多条，逗号分隔")
    private String selectList;
    /**
     * 选择的数据的源数据，方便回显使用
     */
    @Schema(description = "选择的数据的源数据，方便回显使用")
    private List<T> sourceList;

}
