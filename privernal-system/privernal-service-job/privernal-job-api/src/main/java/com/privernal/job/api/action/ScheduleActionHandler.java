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

package com.privernal.job.api.action;

import com.privernal.job.api.dto.ScheduleTaskSnapshot;

/**
 * 动作 SPI。实现必须可重入：幂等键挡住重复投递，
 * 但"处理一半失败后重试"仍要求动作自行核对业务现值。
 *
 * <p>doAction 在执行池线程内调用：组织上下文与豁免已由内核装配，
 * 库名通过 TaskExecutionContext 传递。SPI 放在 job-api 供
 * flow/dynamic 等模块实现各自的动作（如 WORKFLOW_TIMEOUT）。</p>
 *
 * @author saperliu
 */
public interface ScheduleActionHandler {

    /** 动作类型（§3.2） */
    String actionType();

    /** 执行动作 */
    ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context);
}
