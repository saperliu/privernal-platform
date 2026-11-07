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

import com.alibaba.fastjson2.JSONObject;

/**
 * 业务事件对外发布口：动态表单、流程等模块只依赖这个接口，不感知 Webhook 的落库与投递细节。
 * <p>
 * 事件表在 master 库，业务数据在组织各自的业务库，两者不在同一数据源，做不到同一事务；
 * 因此实现侧选择「业务事务提交后写 outbox 再投递」——宁可漏发（有错误日志、可在管理端补），
 * 也不幽灵发（业务已回滚却通知了对端）。
 */
public interface WebhookPublisher {

    /**
     * @param organizationId 事件所属组织，必须显式传入（异步线程与机器调用都没有会话上下文）
     * @param eventType      {@code WebhookEventTypes} 里的事件码
     * @param tableCode      动态表编码，流程事件可传空
     * @param bizId          业务数据ID
     * @param data           业务数据快照，作为请求体里的 data 字段
     */
    void publish(String organizationId, String eventType, String tableCode, String bizId, JSONObject data);

    /**
 * 富事件发布（增量扩展，不改变对外 Webhook 请求体）：
     * 增量携带 before 快照 / 来源标记 / 业务库名 / 操作人 / 级联深度 / 稳定幂等键。
     * 实现侧须在业务提交后同一时机先通知 {@link WebhookInternalListener}（平台内部自动化，
     * 如表单联动引擎），再做 webhook 订阅扇出——内部消费不依赖订阅是否存在。
     *
     * @param event 富事件载体，eventId 由发布点按 §7.5.1 口径生成
     */
    void publish(WebhookEventData event);
}
