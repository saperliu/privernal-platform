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

package com.privernal.message.controller;


import com.privernal.common.log.enums.OperationType;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.message.constant.MessageConstant;
import com.privernal.message.dto.SendMessageDTO;
import com.privernal.message.socket.SocketClientService;
import com.alibaba.fastjson2.JSON;
import com.corundumstudio.socketio.SocketIOClient;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2024-03-04 14:12
 * @Description socket的消息接口
 */
@RestController
@Slf4j
public class SocketController {

    @Autowired
    private SocketClientService socketClientService;

    /**
     * 平台的后台管理界面，统一给在线的用户发送在线通知。
     *
     * @param sendMessageDTO 发送的消息
     *                       发送单个用户
     */
    @Operation(summary = "单个用户发送消息", description = "给PC端在线用户发送消息")
    @PreAuthorize(hasPermi = "system:message:send")
    @Log(title = "Socket 消息推送", operationType = OperationType.OTHER)
    @PostMapping("/back/system/socket/user")
    public void pushMessage(@RequestBody SendMessageDTO sendMessageDTO) {
        log.info("Received chat message: {} ", JSON.toJSONString(sendMessageDTO));

        List<String> userList = sendMessageDTO.getUserList();
        if (!userList.isEmpty()) {
            for (String userId : userList) {
                Map<UUID, SocketIOClient> userClients = socketClientService.getUserClient(userId);
                log.info(" web send message ：{}   {}", userId, JSON.toJSONString(sendMessageDTO));
                // 快照里没有连接就是这个人当前不在线，不是错误
                userClients.forEach((uuid, socketIOClient) -> {
                    log.info(" web send uuid ：  {}", uuid);
                    socketIOClient.sendEvent(MessageConstant.PLATFORM_SOCKET_EVENT_NAME, sendMessageDTO);
                });
            }
        }

    }

    /**
     * 平台的后台管理界面，统一给在线的用户发送在线通知。
     *
     * @param sendMessageDTO 发送的消息
     *                       发送单个用户
     */
    @Operation(summary = "全部在线用户发送消息", description = "给PC端全部在线用户发送消息")
    @PreAuthorize(hasPermi = "system:message:send")
    @Log(title = "Socket 消息推送", operationType = OperationType.OTHER)
    @PostMapping("/back/system/socket/platform")
    public void pushAllMessage(@RequestBody SendMessageDTO sendMessageDTO) {
        log.info("Received chat message: {} ", JSON.toJSONString(sendMessageDTO));
        socketClientService.getAllClient().forEach(uuidSocketIOClientMap -> {
            uuidSocketIOClientMap.keySet().forEach(uuid -> {
                SocketIOClient socketIOClient = uuidSocketIOClientMap.get(uuid);
                if (socketIOClient != null) {
                    socketIOClient.sendEvent(MessageConstant.PLATFORM_SOCKET_EVENT_NAME, sendMessageDTO);
                }
            });
        });
    }
}
