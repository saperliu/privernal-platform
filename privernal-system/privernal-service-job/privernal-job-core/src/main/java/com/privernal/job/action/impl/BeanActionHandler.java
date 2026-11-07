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
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.action.ScheduleActionRegistry;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * BEAN 动作：仅白名单，启动扫描 @ScheduleAction 注册。
 * config: { bean: "call-log-purge" }。
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BeanActionHandler implements ScheduleActionHandler {

    private final ScheduleActionRegistry registry;

    @Override
    public String actionType() {
        return "BEAN";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        Map<String, Object> config = context.getConfig();
        String bean = config.get("bean") == null ? null : String.valueOf(config.get("bean"));
        if (bean == null || bean.isBlank()) {
            return ActionResult.fail("bean 为空");
        }
        ScheduleActionHandler handler = registry.byBeanName(bean);
        if (handler == null) {
            return ActionResult.fail("bean 不在白名单: " + bean);
        }
        return handler.doAction(task, context);
    }
}
