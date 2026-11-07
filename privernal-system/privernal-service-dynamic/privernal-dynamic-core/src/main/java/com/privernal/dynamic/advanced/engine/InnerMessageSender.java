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

package com.privernal.dynamic.advanced.engine;

/**
 * 站内信发送端口（联动引擎 → 消息能力的解耦桥）。
 * <p>
 * privernal-dynamic-core 不依赖 privernal-message 模块（避免反向依赖），
 * 桥接实现在 privernal-server 里注入 ISystemMessageService 完成（参考流程通知的桥接方式）。
 * 未部署桥接 bean 时，MSG_INNER 动作按 FAILED 落日志并告警，不影响其它动作。
 */
public interface InnerMessageSender {

    /**
     * 给一组用户发站内信
     *
     * @param userIds 收件人用户 ID
     * @param title   标题
     * @param content 正文（支持换行）
     * @return 实际投递人数
     */
    int send(java.util.List<String> userIds, String title, String content);
}
