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

package com.privernal.dynamic.advanced.trigger;

import com.privernal.dynamic.advanced.engine.LinkageDispatcher;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 联动 TIMER 触发动作（P1）：调度模块到点回调的接入点。
 * <p>
 * 规则启停/改期时由 {@code LinkageRuleService} 经 ScheduleTaskClient 幂等注册
 * registerCron(orgId, "DESIGNER", "link:{tableCode}:{ruleCode}", cron, "LINKAGE_TIMER", config, database)，
 * 到点后调度内核在本 handler 里回调联动分发器——触发语义（event_id/misfire/日志）
 * 与即时事件完全一致（§7.7.5 的"两种形态同一语义"，此处即"调度模块形态"）。
 * <p>
 * 放在 dynamic-core（实现 privernal-job-api 的 SPI），不依赖 job-core。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkageTimerActionHandler implements ScheduleActionHandler {

    private final LinkageDispatcher linkageDispatcher;

    @Override
    public String actionType() {
        return "LINKAGE_TIMER";
    }

    @Override
    public com.privernal.job.api.action.ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        Map<String, Object> config = context.getConfig();
        String ruleId = config == null ? null : str(config.get("ruleId"));
        if (ruleId == null || ruleId.isEmpty()) {
            return com.privernal.job.api.action.ActionResult.fail("config.ruleId 缺失");
        }
        linkageDispatcher.dispatchTimerFire(ruleId, task.getNextFireTime(),
                task.getOrganizationId(), context.getDatabase());
        return com.privernal.job.api.action.ActionResult.ok("dispatched rule " + ruleId);
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
