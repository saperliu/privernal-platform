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
import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 联动 DATE_FIELD 数据到期动作（DESIGNER_DATA / high-setting ，P2-B1）：
 * 数据写入时由 {@code DateExpiryRegistrar} 按「fieldCode 值 + offsetDays」注册一次性任务
 * （registerOnce(orgId, "DESIGNER_DATA", "linkdate:{tableCode}:{ruleCode}:{dataId}", fireTime, ...)），
 * 到点后调度内核在本 handler 里回调联动分发器，对快照行执行规则动作——
 * 与 TIMER/即时事件同一套触发语义（§7.7.5）。
 * 放在 dynamic-core（实现 privernal-job-api 的 SPI），不依赖 job-core。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkageDateFireActionHandler implements ScheduleActionHandler {

    private final LinkageDispatcher linkageDispatcher;

    @Override
    public String actionType() {
        return "LINKAGE_DATE_FIRE";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        Map<String, Object> config = context.getConfig();
        String ruleId = config == null ? null : str(config.get("ruleId"));
        String dataId = task.getBizContext() == null ? null
                : JSON.parseObject(task.getBizContext()).getString("dataId");
        if (ruleId == null || ruleId.isEmpty()) {
            return ActionResult.fail("config.ruleId 缺失");
        }
        if (dataId == null || dataId.isEmpty()) {
            return ActionResult.fail("biz_context.dataId 缺失");
        }
        linkageDispatcher.dispatchDateFire(ruleId, dataId, task.getNextFireTime(),
                task.getOrganizationId(), context.getDatabase());
        return ActionResult.ok("dispatched date-fire rule=" + ruleId + " dataId=" + dataId);
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
