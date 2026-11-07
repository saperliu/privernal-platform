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
import com.privernal.system.domain.SystemWebhookEvent;
import com.privernal.system.service.WebhookDispatcher;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 系统动作：Webhook outbox 补偿扫描（对 缺口的承接）。
 *
 * <p>进程重启/投递线程池打满期间，system_webhook_event 的 PENDING 行再没有人触发投递。
 * SYSTEM 任务 outbox-compensate（建议每 1 分钟）把"超过 2 分钟仍是 PENDING"的事件
 * 重新交给 WebhookDispatcher.dispatchNow（deliverable 校验与投递记录都在既有链路里）。</p>
 *
 * <p>单实例执行由调度内核选主保证（BEAN 任务只在持调度权实例上跑，§4.2），不再叠加 DB 锁。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduleAction("outbox-compensate")
public class OutboxCompensateActionHandler implements ScheduleActionHandler {

    private final com.privernal.system.mapper.SystemWebhookEventMapper eventMapper;

    private final WebhookDispatcher dispatcher;

    private final ScheduleLogMapper logMapper;

    @Override
    public String actionType() {
        return "BEAN";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        Date threshold = new Date(System.currentTimeMillis() - 2 * 60_000L);
        LambdaQueryWrapper<SystemWebhookEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemWebhookEvent::getStatus, "PENDING")
                .lt(SystemWebhookEvent::getCreatedTime, threshold)
                .last("LIMIT 500");
        List<SystemWebhookEvent> stale = eventMapper.selectList(wrapper);
        int purged = purgeTerminalEvents();
        if (stale.isEmpty()) {
            return ActionResult.ok(purged > 0
                    ? "no stale PENDING events, purged " + purged + " terminal events"
                    : "no stale PENDING events");
        }
        List<String> ids = stale.stream().map(SystemWebhookEvent::getId).toList();
        dispatcher.dispatchNow(ids);
        return ActionResult.ok("re-dispatched " + ids.size() + " stale PENDING events"
                + (purged > 0 ? ", purged " + purged + " terminal events" : ""));
    }

    /** 每轮顺手清一批超期终态事件（每分钟一跑，单批 500 足够追平日增量；PENDING 不在清理范围）。
     * 物理删走自定义 SQL：MP 的 deleteByIds 会被全局逻辑删配置改写成 UPDATE deleted_flag，行数不减 */
    private int purgeTerminalEvents() {
        return logMapper.purgeWebhookEvents(3, 500);
    }
}
