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

package com.privernal.flow.notice;

import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.message.constant.MessageConstant;
import com.privernal.message.dto.SystemMessageDTO;
import com.privernal.message.dto.SystemMessageUserDTO;
import com.privernal.message.service.ISystemMessageService;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.feign.RemoteUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 联动站内信桥接（MSG_INNER）：
 * privernal-dynamic-core 不依赖消息模块，联动引擎面向 com.privernal.dynamic.advanced.engine.InnerMessageSender
 * 编程，桥接实现放在能看到两端的 privernal-server（与流程通知的桥接同构）。
 * 未部署时 MSG_INNER 动作按 FAILED 落日志。
 */
@Slf4j
@Component
public class LinkageInnerMessageSenderBridge implements com.privernal.dynamic.advanced.engine.InnerMessageSender {

    @Autowired
    private ISystemMessageService systemMessageService;

    @Autowired
    private RemoteUserService remoteUserService;

    @Override
    public int send(List<String> userIds, String title, String content) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        List<SystemMessageUserDTO> receivers = new ArrayList<>();
        for (String userId : userIds) {
            if (StringUtils.isEmpty(userId)) {
                continue;
            }
            SystemMessageUserDTO receiver = new SystemMessageUserDTO();
            receiver.setUserId(userId.trim());
            receiver.setNickName(resolveNickName(userId.trim()));
            receivers.add(receiver);
        }
        if (receivers.isEmpty()) {
            return 0;
        }
        SystemMessageDTO message = new SystemMessageDTO();
        message.setMainTitle(title);
        message.setSubTitle(title);
        message.setMessageType(MessageConstant.MESSAGE_TYPE_WORKFLOW);
        message.setContent(content);
        message.setCreatedName(MessageConstant.CREATED_DEFAULT);
        message.setUserList(receivers);
        systemMessageService.saveSystemMessage(message);
        return receivers.size();
    }

    /** 收件人昵称查询失败时降级为用户 id（与流程通知同口径） */
    private String resolveNickName(String userId) {
        try {
            CommonResult<SystemUserDTO> result = remoteUserService.findUserById(userId, Constants.SERVICE_REQUEST);
            if (result != null && result.getCode() == CommonResult.SUCCESS
                    && result.getData() != null && StringUtils.isNotEmpty(result.getData().getNickName())) {
                return result.getData().getNickName();
            }
        } catch (Exception e) {
            log.warn("查询用户昵称失败，降级为用户id userId={}", userId);
        }
        return userId;
    }
}
