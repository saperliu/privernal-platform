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

/**
 * 流程通知发送接口（SPI）
 * flow-core 不直接依赖消息模块，由宿主应用（如 privernal-server）提供实现，
 * 未提供实现时通知仅记录日志，不影响流程运转。
 *
 * @author saperliu
 */
public interface FlowNoticeSender {

    /**
     * 发送流程通知
     *
     * @param notice 通知消息（userIds 非空）
     */
    void send(FlowNoticeMessage notice);
}
