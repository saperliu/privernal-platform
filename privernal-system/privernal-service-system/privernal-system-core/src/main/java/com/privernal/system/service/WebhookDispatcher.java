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

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.system.domain.SystemWebhookDeliveryLog;
import com.privernal.system.domain.SystemWebhookEvent;
import com.privernal.system.domain.SystemWebhookSubscription;
import com.privernal.system.mapper.SystemWebhookDeliveryLogMapper;
import com.privernal.system.mapper.SystemWebhookEventMapper;
import com.privernal.system.mapper.SystemWebhookSubscriptionMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.privernal.system.http.HttpOutboundClient;

import java.nio.charset.StandardCharsets;
import com.privernal.system.http.OutboundRequest;
import com.privernal.system.http.OutboundResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Webhook 投递器：outbox 行由业务事务写入，本类只负责事务提交后的异步投递与有限次退避重试。
 * <p>
 * 签名口径：{@code X-Webhook-Signature = sha256=hmac_sha256(secret, timestamp + "." + body)}，
 * 接收端用同一密钥对「X-Webhook-Timestamp + '.' + 原始请求体」重算即可验签，并结合时间戳拒绝重放。
 * <p>
 * 不引入定时扫描补偿：重试在本任务内退避到上限后置为 DEAD，之后由管理端「重发」按钮重新入队。
 */
@Slf4j
@Component
public class WebhookDispatcher {

    public static final String HEADER_EVENT = "X-Webhook-Event";
    public static final String HEADER_EVENT_ID = "X-Webhook-Event-Id";
    public static final String HEADER_ATTEMPT = "X-Webhook-Attempt";
    public static final String HEADER_TIMESTAMP = "X-Webhook-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Webhook-Signature";

    private static final int QUEUE_CAPACITY = 500;
    private static final int MAX_BACKOFF_MS = 30_000;
    private static final int RESPONSE_BRIEF_LIMIT = 500;
    private static final int ERROR_MSG_LIMIT = 500;

    private final SystemWebhookEventMapper eventMapper;
    private final SystemWebhookSubscriptionMapper subscriptionMapper;
    private final SystemWebhookDeliveryLogMapper deliveryLogMapper;
    private final WebhookSubscriptionService subscriptionService;

    private final HttpOutboundClient outboundClient;

    private ThreadPoolExecutor executor;

    @Autowired
    public WebhookDispatcher(SystemWebhookEventMapper eventMapper,
                             SystemWebhookSubscriptionMapper subscriptionMapper,
                             SystemWebhookDeliveryLogMapper deliveryLogMapper,
                             WebhookSubscriptionService subscriptionService,
                             HttpOutboundClient outboundClient) {
        this.eventMapper = eventMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.deliveryLogMapper = deliveryLogMapper;
        this.subscriptionService = subscriptionService;
        // 传输统一走公共出站栈（双 SSL 客户端/永久失败判定在栈内），本类只保留签名与退避编排
        this.outboundClient = outboundClient;
    }

    @PostConstruct
    public void initExecutor() {
        executor = new ThreadPoolExecutor(4, 8, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread thread = new Thread(r, "webhook-dispatch");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 给 JVM 一个把队列里已排队的投递走完的机会；未完成的留在 outbox（状态 PENDING/FAILED）等待手动重发。
     */
    @PreDestroy
    public void shutdown() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 事务提交后由 WebhookEventService 调用。队列满时不阻塞业务线程，事件留在 outbox 里等待重发。
     */
    public void dispatchNow(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        for (String eventId : eventIds) {
            submit(() -> dispatch(eventId));
        }
    }

    /**
     * Webhook 的异步工作都走这一个池：有界队列 + 守护线程 + 停机时给队列留时间。
     * 不用 {@code @Async} 的默认执行器，因为它是无界队列，下游变慢时会一路积压。
     */
    public void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            log.warn("Webhook 线程池已满（队列 {}），任务被丢弃，数据留在 outbox 里等待手动重发", QUEUE_CAPACITY);
        }
    }

    private void dispatch(String eventId) {
        try {
            SystemWebhookEvent event = eventMapper.selectById(eventId);
            if (event == null || !WebhookEventService.deliverable(event.getStatus())) {
                return;
            }
            // 流水表按组织隔离的读写都依赖上下文；行级部门范围对机器投递没有意义，直接忽略
            OrganizationContextHolder.setOrgAndIgnoreDepartment(event.getOrganizationId(), true);
            SystemWebhookSubscription subscription = subscriptionMapper.selectById(event.getSubscriptionId());
            if (subscription == null) {
                finish(event, WebhookEventService.STATUS_DEAD, null, "订阅不存在：" + event.getSubscriptionId());
                return;
            }
            if (subscription.getStatus() == null || subscription.getStatus() != 1) {
                finish(event, WebhookEventService.STATUS_DEAD, null, "订阅已停用：" + subscription.getSubscriptionName());
                return;
            }
            deliver(event, subscription);
        } catch (Exception e) {
            log.error("Webhook 投递异常 eventId={}", eventId, e);
        } finally {
            OrganizationContextHolder.clear();
        }
    }

    private void deliver(SystemWebhookEvent event, SystemWebhookSubscription subscription) {
        String body = JSON.toJSONString(event.getPayload() == null ? new JSONObject() : event.getPayload());
        String secret = subscriptionService.revealSecret(subscription);
        int maxAttempts = subscription.getMaxRetry() == null ? 6 : subscription.getMaxRetry() + 1;
        int attempt = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
        int timeoutMs = subscription.getTimeoutMs() == null ? 5000 : subscription.getTimeoutMs();
        boolean trustAllSsl = subscription.getSslVerify() != null && subscription.getSslVerify() == 0;

        while (true) {
            AttemptResult result = attempt(trustAllSsl, subscription, event, body, secret, attempt, timeoutMs);
            if (result.success()) {
                finish(event, WebhookEventService.STATUS_SUCCESS, attempt, null, new Date());
                return;
            }
            if (result.permanentFailure()) {
                finish(event, WebhookEventService.STATUS_DEAD, attempt, result.error());
                return;
            }
            if (attempt >= maxAttempts) {
                finish(event, WebhookEventService.STATUS_DEAD, attempt, "重试耗尽：" + result.error());
                return;
            }
            long backoff = backoffMs(attempt);
            finish(event, WebhookEventService.STATUS_FAILED, attempt, result.error(),
                    null, new Date(System.currentTimeMillis() + backoff));
            if (!sleep(backoff)) {
                return;
            }
            attempt++;
        }
    }

    private AttemptResult attempt(boolean trustAllSsl, SystemWebhookSubscription subscription,
                                  SystemWebhookEvent event, String body, String secret,
                                  int attempt, int timeoutMs) {
        OutboundRequest req = buildRequest(trustAllSsl, subscription, event, body, secret, attempt, timeoutMs);
        OutboundResponse resp = outboundClient.send(req);
        Integer statusCode = resp.isExecuted() ? resp.getStatusCode() : null;
        String responseBrief = StringUtils.substring(resp.getBody(), 0, RESPONSE_BRIEF_LIMIT);
        String errorMsg = resp.isSuccess() ? null
                : (resp.isExecuted() ? "响应状态码 " + resp.getStatusCode() : resp.getError());
        saveDeliveryLog(event, subscription, attempt, statusCode, resp.isSuccess(), responseBrief, errorMsg, resp.getCostMs());
        return new AttemptResult(resp.isSuccess(), resp.isPermanentFailure(), errorMsg);
    }

    private OutboundRequest buildRequest(boolean trustAllSsl, SystemWebhookSubscription subscription,
                                         SystemWebhookEvent event, String body, String secret, int attempt, int timeoutMs) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        OutboundRequest req = new OutboundRequest();
        req.setUrl(subscription.getUrl());
        req.setTimeoutMs(timeoutMs);
        req.setBody(body);
        req.setTrustAllSsl(trustAllSsl);
        req.getHeaders().put(HEADER_EVENT, event.getEventType());
        req.getHeaders().put(HEADER_EVENT_ID, event.getId());
        req.getHeaders().put(HEADER_ATTEMPT, String.valueOf(attempt));
        req.getHeaders().put(HEADER_TIMESTAMP, timestamp);
        JSONObject headers = subscription.getHeaders();
        if (headers != null) {
            for (String key : headers.keySet()) {
                if (reservedHeader(key)) {
                    continue;
                }
                Object value = headers.get(key);
                req.getHeaders().put(key, value == null ? "" : String.valueOf(value));
            }
        }
        if (StringUtils.isNotEmpty(secret)) {
            req.getHeaders().put(HEADER_SIGNATURE, "sha256=" + hmacSha256Hex(secret, timestamp + "." + body));
        }
        req.setMethod(StringUtils.isEmpty(subscription.getHttpMethod()) ? "POST" : subscription.getHttpMethod().toUpperCase(Locale.ROOT));
        return req;
    }

    private boolean reservedHeader(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return "content-type".equals(lower)
                || HEADER_TIMESTAMP.toLowerCase(Locale.ROOT).equals(lower)
                || HEADER_SIGNATURE.toLowerCase(Locale.ROOT).equals(lower)
                || HEADER_EVENT.toLowerCase(Locale.ROOT).equals(lower)
                || HEADER_EVENT_ID.toLowerCase(Locale.ROOT).equals(lower)
                || HEADER_ATTEMPT.toLowerCase(Locale.ROOT).equals(lower);
    }

    private void saveDeliveryLog(SystemWebhookEvent event, SystemWebhookSubscription subscription, int attempt,
                                 Integer statusCode, boolean success, String responseBrief, String errorMsg, long costMs) {
        SystemWebhookDeliveryLog record = new SystemWebhookDeliveryLog();
        record.setEventId(event.getId());
        record.setSubscriptionId(subscription.getId());
        record.setAttempt(attempt);
        record.setUrl(StringUtils.substring(subscription.getUrl(), 0, 500));
        record.setStatusCode(statusCode);
        record.setSuccess(success ? 1 : 0);
        record.setResponseBrief(responseBrief);
        record.setErrorMsg(StringUtils.substring(errorMsg, 0, 900));
        record.setCostMs(costMs);
        record.setOrganizationId(event.getOrganizationId());
        record.setCreatedTime(new Date());
        record.setCreatedBy("system");
        try {
            deliveryLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("Webhook 投递记录写入失败 eventId={} attempt={}", event.getId(), attempt, e);
        }
    }

    private void finish(SystemWebhookEvent event, String status, Integer retryCount, String error) {
        finish(event, status, retryCount, error, null, null);
    }

    private void finish(SystemWebhookEvent event, String status, Integer retryCount, String error, Date deliveredTime) {
        finish(event, status, retryCount, error, deliveredTime, null);
    }

    private void finish(SystemWebhookEvent event, String status, Integer retryCount, String error,
                        Date deliveredTime, Date nextRetryTime) {
        LambdaUpdateWrapper<SystemWebhookEvent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SystemWebhookEvent::getId, event.getId());
        wrapper.set(SystemWebhookEvent::getStatus, status);
        if (retryCount != null) {
            wrapper.set(SystemWebhookEvent::getRetryCount, retryCount);
        }
        wrapper.set(SystemWebhookEvent::getNextRetryTime, nextRetryTime);
        wrapper.set(SystemWebhookEvent::getLastError, StringUtils.substring(error, 0, ERROR_MSG_LIMIT));
        wrapper.set(SystemWebhookEvent::getDeliveredTime, deliveredTime);
        eventMapper.update(null, wrapper);
        if (!WebhookEventService.STATUS_SUCCESS.equals(status)) {
            log.warn("Webhook 投递未成功 eventId={} status={} error={}", event.getId(), status, error);
        }
    }

    private long backoffMs(int attempt) {
        long backoff = 1000L << Math.min(Math.max(attempt - 1, 0), 5);
        return Math.min(backoff, MAX_BACKOFF_MS);
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String hmacSha256Hex(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算 Webhook 签名失败", e);
        }
    }

    private record AttemptResult(boolean success, boolean permanentFailure, String error) {
    }
}
