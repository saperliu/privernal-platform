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

package com.privernal.message.service;

import com.aliyuncs.push.model.v20160801.PushResponse;

import java.util.List;

/**
 * APP消息的Service接口
 *
 * @author saperliu
 * @date 2023-07-07
 * @Description 系统消息的Service接口
 */
public interface AppMessageService {


    /**
     * 系统消息
     *
     * @param title    系统消息主题
     * @param subTitle 系统消息副标题
     * @param body     系统消息内容
     * @return 系统消息
     */
    public List<PushResponse> pushAppMessage(String title, String subTitle, String body, String[] accountList);


    /**
     * 系统消息
     *
     * @param title    系统消息主题
     * @param subTitle 系统消息副标题
     * @param body     系统消息内容
     * @return 系统消息
     */
    public PushResponse pushIOSAppMessage(String title, String subTitle, String body, String[] accountList);

    /**
     * 系统消息
     *
     * @param title    系统消息主题
     * @param subTitle 系统消息副标题
     * @param body     系统消息内容
     * @return 系统消息
     */
    public PushResponse pushAndroidAppMessage(String title, String subTitle, String body, String[] accountList);
}
