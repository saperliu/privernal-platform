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

import cn.hutool.json.JSONUtil;
import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import com.privernal.message.constant.MessageConstant;
import com.privernal.message.dto.SystemMessageDTO;
import com.privernal.message.dto.SystemMessageUserDTO;
import com.privernal.message.service.ISystemMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 站内信通知动作（MESSAGE_NOTIFY）。
 *
 * <p>复用 ISystemMessageService.saveSystemMessage——该链路已处理无会话线程的
 * 组织兜底（先例 SystemMessageFlowNoticeSender）。钉钉通道本期不启用
 * （DingTalkService 无发送能力，§15）。只提醒一次：一次性任务发完即 DONE（§10.3 同口径）。</p>
 *
 * <p>config: { title, content, detailUrl?, userIds: [...] }</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageNotifyActionHandler implements ScheduleActionHandler {

    private final ISystemMessageService systemMessageService;

    @Override
    public String actionType() {
        return "MESSAGE_NOTIFY";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        Map<String, Object> config = context.getConfig();
        Object userIdsObj = config.get("userIds");
        if (!(userIdsObj instanceof List<?> rawIds) || rawIds.isEmpty()) {
            return ActionResult.fail("userIds 为空，无人可通知");
        }
        SystemMessageDTO message = new SystemMessageDTO();
        message.setMainTitle(str(config.get("title"), task.getTitle()));
        message.setSubTitle(str(config.get("title"), task.getTitle()));
        message.setMessageType(MessageConstant.MESSAGE_TYPE_NOTICE);
        message.setContent(str(config.get("content"), ""));
        message.setDetailUrl(str(config.get("detailUrl"), null));
        message.setOrganizationId(task.getOrganizationId());
        message.setCreatedName(MessageConstant.CREATED_DEFAULT);
        List<SystemMessageUserDTO> receivers = new ArrayList<>();
        for (Object id : rawIds) {
            SystemMessageUserDTO user = new SystemMessageUserDTO();
            user.setUserId(String.valueOf(id));
            receivers.add(user);
        }
        message.setUserList(receivers);
        systemMessageService.saveSystemMessage(message);
        return ActionResult.ok("notified " + receivers.size() + " users, title=" + message.getMainTitle());
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s.isEmpty() ? fallback : s;
    }
}
