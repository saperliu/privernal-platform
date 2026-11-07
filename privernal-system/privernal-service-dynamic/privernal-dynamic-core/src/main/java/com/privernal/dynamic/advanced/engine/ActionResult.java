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

package com.privernal.dynamic.advanced.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个动作的执行结果（记入 dynamic_linkage_log.action_results）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionResult {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    private String type;

    private String status;

    /** 耗时毫秒 */
    private Long costMs;

    /** 失败原因 */
    private String error;

    /** 尝试次数（线程内退避重试，§7.5.4） */
    private Integer attempts;

    /** 结果摘要（如影响行数、通知人数、预览命中主键），仅日志/试跑展示 */
    private String summary;

    public static ActionResult success(String type, String summary, long costMs) {
        return new ActionResult(type, STATUS_SUCCESS, costMs, null, 1, summary);
    }

    public static ActionResult failed(String type, String error, long costMs, int attempts) {
        return new ActionResult(type, STATUS_FAILED, costMs, error, attempts, null);
    }
}
