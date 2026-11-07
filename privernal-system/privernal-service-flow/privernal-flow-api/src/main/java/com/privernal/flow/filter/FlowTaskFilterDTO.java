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
 * 流程任务查询条件
 *
 * @author saperliu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowTaskFilterDTO extends FilterDTO {

    /** 流程名称 */
    private String flowName;

    /** 业务数据id */
    private String businessId;

    /** 节点编码 */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 到达/办理时间范围-起（yyyy-MM-dd） */
    private String timeStart;

    /** 到达/办理时间范围-止（yyyy-MM-dd，含当日） */
    private String timeEnd;
}
