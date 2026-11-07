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

package com.privernal.message.constant;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2022-02-13 11:38
 * @Description 系统的常量
 */
public class MessageConstant {

    /**
     * 系统名称
     */
    public static final String CREATED_DEFAULT = "系统";

    //    WORKFLOW-审批消息，ALARM-报警消息，NOTICE-通知
    public static final String MESSAGE_TYPE_WORKFLOW = "WORKFLOW";
    public static final String MESSAGE_TYPE_ALARM = "ALARM";
    public static final String MESSAGE_TYPE_NOTICE = "NOTICE";


    /**
     * socket.io的消息主题，
     * 用于分布式的消息分发。因为用户连接的实例未知，所以消息分发需要通过主题来实现。
     * 实现了分布式的socket.io的消息分发。
     */
    public static final String SOCKET_MESSAGE_TOPIC = "SOCKET-MESSAGE-TOPIC";

    // 消息的标签，是向所有用户还是向个别用户发消息
    public static String SOCKET_MESSAGE_ALL = "ALL";
    public static String SOCKET_MESSAGE_USER = "USER";

    //平台的消息事件名称
    public static final String PLATFORM_SOCKET_EVENT_NAME = "platform_message";

}
