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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * OpenAPI 调用统计聚合行（按天 / 按动作）
 */
@Data
@Schema(description = "OpenAPI调用统计聚合行")
public class OpenApiCallStatDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分组键；按天为 yyyy-MM-dd，按动作为动作码")
    private String groupKey;

    @Schema(description = "调用总数")
    private Long totalCount;

    @Schema(description = "成功数")
    private Long successCount;

    @Schema(description = "失败数（含鉴权拒绝与限流）")
    private Long failCount;

    @Schema(description = "平均耗时毫秒")
    private Double avgCostMs;
}
