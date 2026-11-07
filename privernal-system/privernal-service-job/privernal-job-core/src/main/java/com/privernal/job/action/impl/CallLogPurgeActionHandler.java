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

package com.privernal.job.action.impl;

import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleAction;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import com.privernal.job.mapper.ScheduleLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统动作：OpenAPI 调用流水清理（对 缺口的承接）。
 *
 * <p>SYSTEM 任务 call-log-purge（建议每日执行），删除 system_openapi_call_log
 * 中 N 天前的流水；分批 LIMIT 循环，防大事务长锁。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduleAction("call-log-purge")
public class CallLogPurgeActionHandler implements ScheduleActionHandler {

    private static final int RETAIN_DAYS = 90;

    private static final int BATCH = 2000;

    private final ScheduleLogMapper logMapper;

    @Override
    public String actionType() {
        return "BEAN";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        int total = 0;
        int deleted;
        do {
            deleted = logMapper.purgeOpenapiCallLog(RETAIN_DAYS, BATCH);
            total += deleted;
        } while (deleted >= BATCH);
        return ActionResult.ok("purged " + total + " openapi call logs older than " + RETAIN_DAYS + "d");
    }
}
