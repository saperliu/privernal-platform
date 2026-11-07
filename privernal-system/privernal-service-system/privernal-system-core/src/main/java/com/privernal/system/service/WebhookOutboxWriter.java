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

package com.privernal.system.service;

import com.privernal.system.domain.SystemWebhookEvent;
import com.privernal.system.domain.SystemWebhookSubscription;
import com.privernal.system.mapper.SystemWebhookEventMapper;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * outbox 写入：把一次业务变更按匹配到的订阅扇出成待投递行，写入后立即触发异步投递。
 * <p>
 * 为什么独立成一个异步分支，而不是跟着业务代码写：
 * <ol>
 *   <li>调用方（动态表服务）通常处在业务库事务里，Spring 事务会把连接绑到该业务库，
 *       直接写 {@code system_webhook_event} 会落到业务库而不是 master，故 {@code @DS("master")} 换连接；</li>
 *   <li>业务事务还没提交时不能投递（否则业务回滚了通知已发出），所以由调用方在 afterCommit 里提交本方法；</li>
 *   <li>afterCommit 阶段再开事务并不可靠，所以本方法由 {@link WebhookDispatcher#submit} 丢进 Webhook 专用线程池执行
 *       （必须是跨 bean 调用，否则 {@code @DS}/{@code @Transactional} 的代理不生效）。</li>
 * </ol>
 * 代价：不同事件之间不保证先后顺序，写入失败只留错误日志，不会回滚已提交的业务。
 */
@Slf4j
@Component
public class WebhookOutboxWriter {

    @Autowired
    private SystemWebhookEventMapper eventMapper;

    @Autowired
    private WebhookSubscriptionService subscriptionService;

    @Autowired
    private WebhookDispatcher dispatcher;

    @DS("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void enqueue(String organizationId, String eventType, String tableCode, String bizId, JSONObject data) {
        try {
            List<SystemWebhookSubscription> subscriptions =
                    subscriptionService.findMatchingSubscriptions(organizationId, eventType, tableCode);
            if (subscriptions.isEmpty()) {
                return;
            }
            List<String> eventIds = new ArrayList<>();
            Date now = new Date();
            JSONObject payload = buildPayload(eventType, tableCode, bizId, organizationId, data, now);
            for (SystemWebhookSubscription subscription : subscriptions) {
                SystemWebhookEvent event = new SystemWebhookEvent();
                event.setSubscriptionId(subscription.getId());
                event.setEventType(eventType);
                event.setTableCode(tableCode);
                event.setBizId(bizId);
                event.setPayload(payload);
                event.setStatus(WebhookEventService.STATUS_PENDING);
                event.setRetryCount(0);
                event.setNextRetryTime(now);
                event.setOrganizationId(organizationId);
                event.setCreatedTime(now);
                eventMapper.insert(event);
                eventIds.add(event.getId());
            }
            dispatcher.dispatchNow(eventIds);
        } catch (Exception e) {
            log.error("Webhook 事件写入 outbox 失败 organizationId={} eventType={} tableCode={} bizId={}",
                    organizationId, eventType, tableCode, bizId, e);
        }
    }

    private JSONObject buildPayload(String eventType, String tableCode, String bizId,
                                    String organizationId, JSONObject data, Date occurredAt) {
        JSONObject payload = new JSONObject();
        payload.put("eventType", eventType);
        payload.put("tableCode", tableCode);
        payload.put("bizId", bizId);
        payload.put("organizationId", organizationId);
        payload.put("occurredAt", occurredAt.getTime());
        payload.put("data", data == null ? new JSONObject() : data);
        return payload;
    }
}
