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

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.api.WebhookEventData;
import com.privernal.system.api.WebhookInternalListener;
import com.privernal.system.api.WebhookPublisher;
import com.privernal.system.constant.WebhookEventTypes;
import com.privernal.system.domain.SystemWebhookDeliveryLog;
import com.privernal.system.domain.SystemWebhookEvent;
import com.privernal.system.domain.SystemWebhookSubscription;
import com.privernal.system.dto.WebhookDeliveryLogDTO;
import com.privernal.system.dto.WebhookEventDTO;
import com.privernal.system.filter.WebhookEventFilterDTO;
import com.privernal.system.mapper.SystemWebhookDeliveryLogMapper;
import com.privernal.system.mapper.SystemWebhookEventMapper;
import com.privernal.system.mapper.SystemWebhookSubscriptionMapper;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Webhook 事件的发布入口与查询。
 * <p>
 * 发布走 {@link WebhookOutboxWriter}：落库与投递都在业务事务提交之后（见该类的说明），
 * 这里只负责参数校验和「等业务提交」这个时机。
 */
@Slf4j
@Service
public class WebhookEventService implements WebhookPublisher {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DELIVERING = "DELIVERING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD = "DEAD";

    @Autowired
    private SystemWebhookEventMapper eventMapper;

    @Autowired
    private SystemWebhookDeliveryLogMapper deliveryLogMapper;

    @Autowired
    private SystemWebhookSubscriptionMapper subscriptionMapper;

    @Autowired
    private WebhookOutboxWriter outboxWriter;

    @Autowired
    private WebhookDispatcher dispatcher;

    /**
     * 平台内部监听器（如表单联动引擎）。ObjectProvider 惰性解析：
     * 监听器实现多在业务模块（privernal-dynamic-core），而业务模块又依赖本接口发布事件，
     * 直接注入 List 会形成 bean 环。
     */
    @Autowired
    private ObjectProvider<WebhookInternalListener> internalListeners;

    @Override
    public void publish(String organizationId, String eventType, String tableCode, String bizId, JSONObject data) {
        if (!WebhookEventTypes.isKnown(eventType)) {
            log.warn("未知 Webhook 事件类型，跳过：{}", eventType);
            return;
        }
        WebhookEventData event = new WebhookEventData();
        event.setEventType(eventType);
        event.setOrganizationId(organizationId);
        event.setTableCode(tableCode);
        event.setBizId(bizId);
        event.setData(data);
        event.setOccurredAt(new Date());
        publish(event);
    }

    /**
 * 富事件发布：空组织/未知类型校验之后、订阅扇出之前，
     * 内部监听器与 webhook 走同一 afterCommit 时机——内部自动化不依赖订阅是否存在
     * （无 webhook 订阅的组织联动也要触发，这是 v1.2 评审的关键用例）。
     * <p>
     * 监听器异常只记日志：联动失败不回滚已提交业务，也不能挡住 webhook 投递。
     */
    @Override
    public void publish(WebhookEventData event) {
        if (event == null) {
            return;
        }
        if (!WebhookEventTypes.isKnown(event.getEventType())) {
            log.warn("未知 Webhook 事件类型，跳过：{}", event.getEventType());
            return;
        }
        if (StringUtils.isEmpty(event.getOrganizationId())) {
            log.warn("Webhook 事件缺少组织归属，跳过 eventType={} tableCode={} bizId={}",
                    event.getEventType(), event.getTableCode(), event.getBizId());
            return;
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(new Date());
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 业务还没提交就先攥着：提交成功才允许通知对端
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyInternalListeners(event);
                    enqueueAsync(event.getOrganizationId(), event.getEventType(), event.getTableCode(),
                            event.getBizId(), event.getData());
                }
            });
        } else {
            notifyInternalListeners(event);
            enqueueAsync(event.getOrganizationId(), event.getEventType(), event.getTableCode(),
                    event.getBizId(), event.getData());
        }
    }

    private void notifyInternalListeners(WebhookEventData event) {
        for (WebhookInternalListener listener : internalListeners) {
            try {
                listener.onEventCommitted(event);
            } catch (Exception e) {
                log.error("内部事件监听器处理失败 listener={} eventType={} tableCode={} bizId={}",
                        listener.getClass().getSimpleName(), event.getEventType(), event.getTableCode(),
                        event.getBizId(), e);
            }
        }
    }

    /**
     * 落库丢给 Webhook 专用线程池：既避开「afterCommit 里再开事务」这个坑，也不占业务线程
     */
    /**
     * 事件行只存 subscription_id，列表要能看出是哪条订阅投递失败：一次 in 查询补齐名称
     */
    private void fillSubscriptionNames(List<WebhookEventDTO> events) {
        if (events.isEmpty()) {
            return;
        }
        List<String> subscriptionIds = new ArrayList<>();
        for (WebhookEventDTO dto : events) {
            if (StringUtils.isNotEmpty(dto.getSubscriptionId())) {
                subscriptionIds.add(dto.getSubscriptionId());
            }
        }
        if (subscriptionIds.isEmpty()) {
            return;
        }
        Map<String, String> names = new HashMap<>();
        for (SystemWebhookSubscription subscription : subscriptionMapper.selectBatchIds(subscriptionIds)) {
            names.put(subscription.getId(), subscription.getSubscriptionName());
        }
        for (WebhookEventDTO dto : events) {
            dto.setSubscriptionName(names.get(dto.getSubscriptionId()));
        }
    }

    private void enqueueAsync(String organizationId, String eventType, String tableCode, String bizId, JSONObject data) {
        dispatcher.submit(() -> outboxWriter.enqueue(organizationId, eventType, tableCode, bizId, data));
    }

    /**
     * 手动重发：把事件重置为待投递再走一次异步投递
     */
    public WebhookEventDTO resend(String eventId) {
        SystemWebhookEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new ServiceException("事件不存在：" + eventId);
        }
        LambdaUpdateWrapper<SystemWebhookEvent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SystemWebhookEvent::getId, eventId);
        wrapper.set(SystemWebhookEvent::getStatus, STATUS_PENDING);
        wrapper.set(SystemWebhookEvent::getRetryCount, 0);
        wrapper.set(SystemWebhookEvent::getNextRetryTime, new Date());
        wrapper.set(SystemWebhookEvent::getLastError, null);
        wrapper.set(SystemWebhookEvent::getDeliveredTime, null);
        eventMapper.update(null, wrapper);
        dispatcher.dispatchNow(new ArrayList<>(List.of(eventId)));
        return detail(eventId);
    }

    @Transactional(readOnly = true)
    public List<WebhookEventDTO> selectList(WebhookEventFilterDTO filter) {
        LambdaQueryWrapper<SystemWebhookEvent> wrapper = new LambdaQueryWrapper<>();
        if (filter != null) {
            wrapper.eq(StringUtils.isNotEmpty(filter.getSubscriptionId()),
                    SystemWebhookEvent::getSubscriptionId, filter.getSubscriptionId());
            wrapper.eq(StringUtils.isNotEmpty(filter.getEventType()), SystemWebhookEvent::getEventType, filter.getEventType());
            wrapper.eq(StringUtils.isNotEmpty(filter.getTableCode()), SystemWebhookEvent::getTableCode, filter.getTableCode());
            wrapper.eq(StringUtils.isNotEmpty(filter.getBizId()), SystemWebhookEvent::getBizId, filter.getBizId());
            wrapper.eq(StringUtils.isNotEmpty(filter.getStatus()), SystemWebhookEvent::getStatus, filter.getStatus());
            wrapper.eq(StringUtils.isNotEmpty(filter.getOrganizationId()),
                    SystemWebhookEvent::getOrganizationId, filter.getOrganizationId());
            wrapper.ge(StringUtils.isNotEmpty(filter.getBeginTime()), SystemWebhookEvent::getCreatedTime, filter.getBeginTime());
            wrapper.le(StringUtils.isNotEmpty(filter.getEndTime()), SystemWebhookEvent::getCreatedTime, filter.getEndTime());
        }
        wrapper.orderByDesc(SystemWebhookEvent::getCreatedTime);
        List<WebhookEventDTO> result = new ArrayList<>();
        for (SystemWebhookEvent event : eventMapper.selectList(wrapper)) {
            WebhookEventDTO dto = toDTO(event);
            dto.setPayload(null);
            result.add(dto);
        }
        fillSubscriptionNames(result);
        return result;
    }

    @Transactional(readOnly = true)
    public WebhookEventDTO detail(String id) {
        SystemWebhookEvent event = eventMapper.selectById(id);
        if (event == null) {
            throw new ServiceException("事件不存在：" + id);
        }
        WebhookEventDTO dto = toDTO(event);
        dto.setDeliveryLogs(deliveryLogs(id));
        fillSubscriptionNames(List.of(dto));
        return dto;
    }

    /**
     * 某个事件的全部投递尝试，按第几次尝试升序
     */
    @Transactional(readOnly = true)
    public List<WebhookDeliveryLogDTO> deliveryLogs(String eventId) {
        LambdaQueryWrapper<SystemWebhookDeliveryLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemWebhookDeliveryLog::getEventId, eventId);
        wrapper.orderByAsc(SystemWebhookDeliveryLog::getAttempt);
        List<WebhookDeliveryLogDTO> result = new ArrayList<>();
        for (SystemWebhookDeliveryLog row : deliveryLogMapper.selectList(wrapper)) {
            result.add(toLogDTO(row));
        }
        return result;
    }

    private WebhookEventDTO toDTO(SystemWebhookEvent event) {
        WebhookEventDTO dto = new WebhookEventDTO();
        dto.setId(event.getId());
        dto.setSubscriptionId(event.getSubscriptionId());
        dto.setEventType(event.getEventType());
        dto.setTableCode(event.getTableCode());
        dto.setBizId(event.getBizId());
        dto.setPayload(event.getPayload());
        dto.setStatus(event.getStatus());
        dto.setRetryCount(event.getRetryCount());
        dto.setNextRetryTime(event.getNextRetryTime());
        dto.setLastError(event.getLastError());
        dto.setDeliveredTime(event.getDeliveredTime());
        dto.setCreatedTime(event.getCreatedTime());
        dto.setOrganizationId(event.getOrganizationId());
        return dto;
    }

    private WebhookDeliveryLogDTO toLogDTO(SystemWebhookDeliveryLog row) {
        WebhookDeliveryLogDTO dto = new WebhookDeliveryLogDTO();
        dto.setId(row.getId());
        dto.setEventId(row.getEventId());
        dto.setSubscriptionId(row.getSubscriptionId());
        dto.setAttempt(row.getAttempt());
        dto.setUrl(row.getUrl());
        dto.setStatusCode(row.getStatusCode());
        dto.setSuccess(row.getSuccess());
        dto.setResponseBrief(row.getResponseBrief());
        dto.setErrorMsg(row.getErrorMsg());
        dto.setCostMs(row.getCostMs());
        dto.setCreatedTime(row.getCreatedTime());
        return dto;
    }

    /**
     * 只给投递器用的状态校验：PENDING/FAILED 才可投递
     */
    public static boolean deliverable(String status) {
        return Arrays.asList(STATUS_PENDING, STATUS_FAILED).contains(status);
    }
}
