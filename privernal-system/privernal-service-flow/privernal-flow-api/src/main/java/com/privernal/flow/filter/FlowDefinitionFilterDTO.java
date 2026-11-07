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

package com.privernal.flow.filter;

import com.privernal.common.web.domain.FilterDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程定义查询条件
 *
 * @author saperliu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowDefinitionFilterDTO extends FilterDTO {

    /** 流程编码 */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 流程类别 */
    private String category;

    /** 发布状态（0未发布 1已发布 9已失效） */
    private Integer isPublish;
}
