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

package com.privernal.flow.handler;

import com.privernal.flow.mapper.FlowUserDelegateMapper;
import com.privernal.system.event.UserAccountDisabledEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 用户停用联动监听（R1-8 权限治理③）
 *
 * 订阅 system 模块的用户停用事件：失效该用户作为被代理人/代理人的全部流程代理规则，
 * 与停用在同一事务内提交（监听失败仅告警不阻断停用）。
 * 待办转办不在此处理——由「离职移交」（/back/flow/task/handover）前置完成。
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：停用→流程代理规则失效
 */
@Slf4j
@Component
public class UserAccountDisabledLinkageListener {

    @Autowired
    private FlowUserDelegateMapper delegateMapper;

    @EventListener
    public void onUserDisabled(UserAccountDisabledEvent event) {
        try {
            int rows = delegateMapper.disableByUserId(event.getUserId(), new Date());
            if (rows > 0) {
                log.info("[R1-8] 用户停用联动：失效 {} 条流程代理规则 userId={}", rows, event.getUserId());
            }
        } catch (Exception e) {
            log.warn("[R1-8] 用户停用联动失败（流程代理规则未失效，可手工处理） userId={}：{}", event.getUserId(), e.getMessage());
        }
    }
}
