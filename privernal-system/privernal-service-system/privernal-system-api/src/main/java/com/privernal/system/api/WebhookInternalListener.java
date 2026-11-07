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

package com.privernal.system.api;

/**
 * 平台内部事件监听器（v1.2 更正的联动接入口）。
 * <p>
 * 实现在业务事务<b>提交后</b>被回调，回调时机与 Webhook 订阅扇出同一（afterCommit），
 * 但<b>先于、独立于</b>订阅判断——{@code WebhookOutboxWriter.enqueue} 在组织没有任何
 * webhook 订阅时直接 return 且不落事件行，因此联动（及其它内部自动化）必须挂在这里，
 * 否则没有 webhook 订阅的组织联动会静默永不触发。
 * <p>
 * 实现方约定：
 * <ul>
 *   <li>不得向 {@code SystemWebhookEvent} 行写任何状态（那是 webhook 投递自身的状态机）；</li>
 *   <li>自行保证耗时可控（回调跑在业务线程 afterCommit 里，应快速移交自有线程池）；</li>
 *   <li>抛出的异常会被记录日志后吞掉，不影响 webhook 扇出，也不影响已提交的业务。</li>
 * </ul>
 */
public interface WebhookInternalListener {

    /**
     * 业务事务提交后的事件回调。
     *
     * @param event 富事件载体（含 before 快照 / sourceTag / databaseName / eventId）
     */
    void onEventCommitted(WebhookEventData event);
}
