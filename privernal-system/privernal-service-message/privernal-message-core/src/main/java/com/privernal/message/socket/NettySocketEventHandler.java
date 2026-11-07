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

package com.privernal.message.socket;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.message.constant.MessageConstant;
import com.privernal.message.dto.SendMessageDTO;
import com.alibaba.fastjson2.JSON;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2024-03-04 14:12
 * @Description socket的事件处理器
 */
@Component
@Slf4j
public class NettySocketEventHandler {

    @Autowired
    private SocketIOServer socketIoServer;

    @Autowired
    private SocketClientService socketClientService;

    @Value("${socketio.application.name}")
    private String serverName;

    @Value("${socketio.reg-server}")
    private String host;


    private void start() throws Exception {
        //注册到Nacos里
//        registerNamingService(serverName, String.valueOf(socketIoServer.getConfiguration().getPort()));
    }


    @PostConstruct
    private void autoStartup() {
        try {
            socketIoServer.start();
            start();
            log.info("-------------- start socket server  ----------");
        } catch (Exception ex) {
            log.error("SocketIOServer启动失败", ex);
        }
    }

    @PreDestroy
    private void autoStop() {
        socketIoServer.stop();
    }

    //socket事件消息接收入口
    @OnEvent(value = MessageConstant.PLATFORM_SOCKET_EVENT_NAME) //value值与前端自行商定
    public void onEvent(SocketIOClient client, AckRequest ackRequest, SendMessageDTO data) {
//            client.sendEvent("message_event", "已成功接收数据"); //向前端发送接收数据成功标识
        log.info("socket  event  {}", JSON.toJSONString(data));
    }

    //socket添加@OnDisconnect事件，客户端断开连接时调用，刷新客户端信息
    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {

        String userId = client.getHandshakeData().getSingleUrlParam("userId");
        UUID sessionId = client.getSessionId();

        log.info("socket  Disconnect {} {}", userId, sessionId);
        socketClientService.deleteSessionClientByUserId(userId, sessionId);

        log.info("socket  Disconnect {} {}", userId, sessionId);
        client.disconnect();
    }

    //socket添加connect事件，当客户端发起连接时调用
    @OnConnect
    public void onConnect(SocketIOClient client) {
        if (client == null) {
            log.error("客户端为空");
            return;
        }
        HandshakeData handshake = client.getHandshakeData();
        String userId = handshake.getSingleUrlParam(SocketHandshakePolicy.USER_ID_PARAM);
        String token = handshake.getSingleUrlParam(SocketHandshakePolicy.TOKEN_PARAM);
        // 握手期已经拦过一道，这里是第二道：连接数上限只在登记时才知道，
        // 而超限的新连接如果不主动断开，就会带着「没登记」的状态一直挂在 worker 线程上——
        // 原实现正是这样：鉴权失败只打日志，连接照旧留着。
        if (!SocketHandshakePolicy.permits(userId, token, SecurityUtils::checkTokenLogin)) {
            log.warn("socket 连接被拒：userId={} 鉴权未通过，已主动断开", userId);
            client.disconnect();
            return;
        }
        UUID sessionId = client.getSessionId();
        if (!socketClientService.saveClient(userId, sessionId, client)) {
            client.disconnect();
            return;
        }
        log.info(" socket connect {}  {}", userId, sessionId);
    }

}

