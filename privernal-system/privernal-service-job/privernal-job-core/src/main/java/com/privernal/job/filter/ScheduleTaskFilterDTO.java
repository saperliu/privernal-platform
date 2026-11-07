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

package com.privernal.job.filter;

import com.privernal.common.web.domain.FilterDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务列表过滤器（组织/应用/来源/触发类型/状态/动作类型/关键字）。
 *
 * @author saperliu
 */
@Tag(name = "ScheduleTaskFilterDTO", description = "定时任务查询过滤器")
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleTaskFilterDTO extends FilterDTO {

    private static final long serialVersionUID = 1L;

    @Schema(description = "组织id（超管可指定，默认当前组织）")
    private String organizationId;

    @Schema(description = "应用id")
    private String appId;

    @Schema(description = "来源 WORKFLOW/DESIGNER/DESIGNER_DATA/SYSTEM/OPENAPI/MANUAL")
    private String source;

    @Schema(description = "触发类型 CRON/FIXED_DELAY/ONCE")
    private String triggerType;

    @Schema(description = "状态 READY/PAUSED/TRIGGERED/RUNNING/DONE/CANCELLED")
    private String status;

    @Schema(description = "动作类型")
    private String actionType;

    @Schema(description = "关键字（名称/编码/业务键）")
    private String keyword;

    @Schema(description = "负责人（创建人）")
    private String createdBy;
}
