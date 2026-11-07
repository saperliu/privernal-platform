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

import cn.dev33.satoken.same.SaSameUtil;
import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleAction;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统动作：sa-token same-token 主动刷新（§17 SYSTEM 存量任务迁入）。
 *
 * <p>原 SaIdTokenRefreshTask 的 @Scheduled 已移除（该迁移要求由本模块统一承载），
 * SYSTEM 任务 same-token-refresh 以 FIXED_DELAY 300s 等价接管；调度链路单主 +
 * 对账保证不丢周期，refreshToken 本身幂等可重入。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@ScheduleAction("same-token-refresh")
public class SameTokenRefreshActionHandler implements ScheduleActionHandler {

    @Override
    public String actionType() {
        return "BEAN";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        SaSameUtil.refreshToken();
        return ActionResult.ok("same-token refreshed");
    }
}
