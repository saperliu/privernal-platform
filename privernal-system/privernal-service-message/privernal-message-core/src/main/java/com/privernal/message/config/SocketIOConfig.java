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

package com.privernal.message.config;

import com.privernal.message.socket.SocketHandshakePolicy;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2024-03-04 14:12
 * @Description socket服务器的配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "socketio")
public class SocketIOConfig {

    private String host;
    private String context;
    private Integer port;
    private int bossCount;
    private int workCount;
    private boolean allowCustomRequests;
    private int upgradeTimeout;
    private int pingTimeout;
    private int pingInterval;

    /**
     * 单帧与 HTTP 交互的最大长度。
     * <p>
     * yaml 里这两个键从项目建立起就写着（{@code maxFramePayloadLength: 1048576}），但 POJO 一直没有
     * 对应字段——{@code @ConfigurationProperties} 对不上的键是静默丢弃的，所以这两条限制从未生效过，
     * 服务端用的是 netty-socketio 自己的默认值。补上字段才是配置，配了不生效比没配更危险。
     */
    private int maxFramePayloadLength = 1048576;

    private int maxHttpContentLength = 1048576;

    @Bean
    public SocketIOServer socketIOServer(SocketHandshakePolicy handshakePolicy) {
        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setTcpNoDelay(true);
        socketConfig.setSoLinger(0);
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setSocketConfig(socketConfig);
        config.setHostname(host);
        config.setPort(port);
        config.setBossThreads(bossCount);
        config.setContext(context);
        config.setWorkerThreads(workCount);
        config.setAllowCustomRequests(allowCustomRequests);
        config.setUpgradeTimeout(upgradeTimeout);
        config.setPingTimeout(pingTimeout);
        config.setPingInterval(pingInterval);
        config.setMaxFramePayloadLength(maxFramePayloadLength);
        config.setMaxHttpContentLength(maxHttpContentLength);
        // 鉴权放在握手期：@OnConnect 时连接已经建立，那时才拒绝等于让匿名客户端白占一轮 worker
        config.setAuthorizationListener(handshakePolicy::authorize);
        return new SocketIOServer(config);
    }

    @Bean
    public SpringAnnotationScanner springAnnotationScanner(SocketIOServer socketIOServer) {
        return new SpringAnnotationScanner(socketIOServer);
    }
}
