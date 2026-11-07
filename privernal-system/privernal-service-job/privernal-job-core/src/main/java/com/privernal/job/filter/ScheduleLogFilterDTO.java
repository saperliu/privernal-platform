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
 * 执行记录列表过滤器（；时间条件一律用 fire_time，分区键口径）。
 *
 * @author saperliu
 */
@Tag(name = "ScheduleLogFilterDTO", description = "执行记录查询过滤器")
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleLogFilterDTO extends FilterDTO {

    private static final long serialVersionUID = 1L;

    @Schema(description = "组织id（超管可指定，默认当前组织）")
    private String organizationId;

    @Schema(description = "任务id")
    private String taskId;

    @Schema(description = "状态 SUCCESS/FAILED/TIMEOUT/SKIPPED_*/CANCELLED/RUNNING")
    private String status;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "计划触发时间起（yyyy-MM-dd HH:mm:ss）")
    private String fireTimeBegin;

    @Schema(description = "计划触发时间止（yyyy-MM-dd HH:mm:ss）")
    private String fireTimeEnd;
}
