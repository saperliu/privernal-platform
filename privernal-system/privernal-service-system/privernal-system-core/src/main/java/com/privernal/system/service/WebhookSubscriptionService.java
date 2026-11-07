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
import com.privernal.common.core.utils.net.OutboundHttpGuard;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.system.constant.WebhookEventTypes;
import com.privernal.system.domain.SystemWebhookSubscription;
import com.privernal.system.dto.WebhookSubscriptionDTO;
import com.privernal.system.filter.WebhookSubscriptionFilterDTO;
import com.privernal.system.mapper.SystemWebhookSubscriptionMapper;
import com.privernal.system.support.MachineSecretCipher;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Webhook 订阅管理。签名密钥与 OpenAPI Secret 同理：可逆存储，明文只在创建/轮换时返回一次。
 */
@Slf4j
@Service
public class WebhookSubscriptionService {

    @Autowired
    private SystemWebhookSubscriptionMapper subscriptionMapper;

    @Autowired
    private MachineSecretCipher secretCipher;

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionDTO> selectList(WebhookSubscriptionFilterDTO filter) {
        LambdaQueryWrapper<SystemWebhookSubscription> wrapper = new LambdaQueryWrapper<>();
        if (filter != null) {
            wrapper.like(StringUtils.isNotEmpty(filter.getSubscriptionName()),
                    SystemWebhookSubscription::getSubscriptionName, filter.getSubscriptionName());
            wrapper.eq(StringUtils.isNotEmpty(filter.getAppId()), SystemWebhookSubscription::getAppId, filter.getAppId());
            wrapper.eq(StringUtils.isNotEmpty(filter.getTableCode()), SystemWebhookSubscription::getTableCode, filter.getTableCode());
            wrapper.eq(filter.getStatus() != null, SystemWebhookSubscription::getStatus, filter.getStatus());
            wrapper.eq(StringUtils.isNotEmpty(filter.getOrganizationId()),
                    SystemWebhookSubscription::getOrganizationId, filter.getOrganizationId());
        }
        wrapper.orderByDesc(SystemWebhookSubscription::getCreatedTime);
        List<WebhookSubscriptionDTO> result = new ArrayList<>();
        for (SystemWebhookSubscription entity : subscriptionMapper.selectList(wrapper)) {
            result.add(toDTO(entity));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public WebhookSubscriptionDTO selectById(String id) {
        SystemWebhookSubscription entity = subscriptionMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException("订阅不存在：" + id);
        }
        return toDTO(entity);
    }

    @Transactional
    public WebhookSubscriptionDTO create(WebhookSubscriptionDTO dto) {
        if (!secretCipher.isConfigured()) {
            throw new ServiceException("服务端未配置 openapi.secret-master-key，无法安全存储签名密钥");
        }
        validate(dto);
        SystemWebhookSubscription entity = new SystemWebhookSubscription();
        applyEditableFields(entity, dto);
        entity.setSubscriptionName(dto.getSubscriptionName());
        String secret = secretCipher.generateSecret();
        entity.setSecretCipher(secretCipher.encrypt(secret));
        entity.setSecretPrefix(MachineSecretCipher.prefix(secret));
        entity.setSecretLast4(MachineSecretCipher.last4(secret));
        entity.setEvents(toEventsJson(dto.getEvents()));
        entity.setHeaders(dto.getHeaders());
        entity.setHttpMethod(StringUtils.isEmpty(dto.getHttpMethod()) ? "POST" : dto.getHttpMethod().toUpperCase());
        entity.setSslVerify(dto.getSslVerify() == null ? 1 : dto.getSslVerify());
        entity.setTimeoutMs(dto.getTimeoutMs() == null ? 5000 : dto.getTimeoutMs());
        entity.setMaxRetry(dto.getMaxRetry() == null ? 5 : dto.getMaxRetry());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        subscriptionMapper.insert(entity);
        WebhookSubscriptionDTO result = toDTO(entity);
        result.setSecret(secret);
        return result;
    }

    @Transactional
    public WebhookSubscriptionDTO update(WebhookSubscriptionDTO dto) {
        SystemWebhookSubscription entity = subscriptionMapper.selectById(dto.getId());
        if (entity == null) {
            throw new ServiceException("订阅不存在：" + dto.getId());
        }
        validate(dto);
        applyEditableFields(entity, dto);
        entity.setEvents(toEventsJson(dto.getEvents()));
        entity.setHeaders(dto.getHeaders());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        subscriptionMapper.updateById(entity);
        return toDTO(entity);
    }

    @Transactional
    public WebhookSubscriptionDTO rotateSecret(String id) {
        SystemWebhookSubscription entity = subscriptionMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException("订阅不存在：" + id);
        }
        String secret = secretCipher.generateSecret();
        entity.setSecretCipher(secretCipher.encrypt(secret));
        entity.setSecretPrefix(MachineSecretCipher.prefix(secret));
        entity.setSecretLast4(MachineSecretCipher.last4(secret));
        subscriptionMapper.updateById(entity);
        WebhookSubscriptionDTO result = toDTO(entity);
        result.setSecret(secret);
        return result;
    }

    @Transactional
    public int deleteByIds(String[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return subscriptionMapper.deleteBatchIds(Arrays.asList(ids));
    }

    public String revealSecret(SystemWebhookSubscription subscription) {
        if (StringUtils.isEmpty(subscription.getSecretCipher())) {
            return null;
        }
        return secretCipher.decrypt(subscription.getSecretCipher());
    }

    /**
     * 事件扇出的订阅匹配：组织内、启用中、表范围命中。
     * <p>
     * events 是 JSON 列，SQL 里做数组包含不可靠，所以先按组织捞再在内存里判定
     * （一个组织的有效订阅数量级很小，不需要为它建索引）。
     */
    @Transactional(readOnly = true)
    public List<SystemWebhookSubscription> findMatchingSubscriptions(String organizationId, String eventType,
                                                                    String tableCode) {
        if (StringUtils.isEmpty(organizationId) || StringUtils.isEmpty(eventType)) {
            return new ArrayList<>();
        }
        Boolean oldIgnored = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            LambdaQueryWrapper<SystemWebhookSubscription> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemWebhookSubscription::getOrganizationId, organizationId);
            wrapper.eq(SystemWebhookSubscription::getStatus, 1);
            List<SystemWebhookSubscription> matched = new ArrayList<>();
            for (SystemWebhookSubscription subscription : subscriptionMapper.selectList(wrapper)) {
                if (!parseEvents(subscription.getEvents()).contains(eventType)) {
                    continue;
                }
                if (StringUtils.isNotEmpty(subscription.getTableCode())
                        && !subscription.getTableCode().equals(tableCode)) {
                    continue;
                }
                matched.add(subscription);
            }
            return matched;
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnored);
        }
    }

    private void validate(WebhookSubscriptionDTO dto) {
        if (StringUtils.isEmpty(dto.getSubscriptionName())) {
            throw new ServiceException("订阅名称不能为空");
        }
        if (StringUtils.isEmpty(dto.getUrl())) {
            throw new ServiceException("回调地址不能为空");
        }
        String url = dto.getUrl().toLowerCase();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ServiceException("回调地址必须是 http(s) 地址");
        }
        // 配置时前置同一份出站闸门（协议/IP 字面量/DNS 逐地址否决）；投递时 HttpOutboundClient 还会再拦一次
        OutboundHttpGuard.check(dto.getUrl(), "Webhook 回调");
        if (dto.getEvents() == null || dto.getEvents().isEmpty()) {
            throw new ServiceException("至少要订阅一个事件类型");
        }
        for (String event : dto.getEvents()) {
            if (!WebhookEventTypes.isKnown(event)) {
                throw new ServiceException("未知的事件类型：" + event);
            }
        }
        if (dto.getMaxRetry() != null && (dto.getMaxRetry() < 0 || dto.getMaxRetry() > 10)) {
            throw new ServiceException("重试次数需在 0-10 之间");
        }
        if (dto.getTimeoutMs() != null && (dto.getTimeoutMs() < 500 || dto.getTimeoutMs() > 30000)) {
            throw new ServiceException("超时需在 500-30000 毫秒之间");
        }
    }

    private void applyEditableFields(SystemWebhookSubscription entity, WebhookSubscriptionDTO dto) {
        entity.setSubscriptionName(dto.getSubscriptionName());
        entity.setAppId(dto.getAppId());
        entity.setUrl(dto.getUrl());
        entity.setTableCode(dto.getTableCode());
        if (StringUtils.isNotEmpty(dto.getHttpMethod())) {
            entity.setHttpMethod(dto.getHttpMethod().toUpperCase());
        } else if (StringUtils.isEmpty(entity.getHttpMethod())) {
            entity.setHttpMethod("POST");
        }
        if (dto.getSslVerify() != null) {
            entity.setSslVerify(dto.getSslVerify());
        } else if (entity.getSslVerify() == null) {
            entity.setSslVerify(1);
        }
        if (dto.getTimeoutMs() != null) {
            entity.setTimeoutMs(dto.getTimeoutMs());
        } else if (entity.getTimeoutMs() == null) {
            entity.setTimeoutMs(5000);
        }
        if (dto.getMaxRetry() != null) {
            entity.setMaxRetry(dto.getMaxRetry());
        } else if (entity.getMaxRetry() == null) {
            entity.setMaxRetry(5);
        }
        entity.setRemark(dto.getRemark());
    }

    private JSONObject toEventsJson(List<String> events) {
        JSONObject json = new JSONObject();
        json.put("events", events == null ? new ArrayList<String>() : events);
        return json;
    }

    public static List<String> parseEvents(JSONObject json) {
        if (json == null) {
            return new ArrayList<>();
        }
        JSONArray array = json.getJSONArray("events");
        return array == null ? new ArrayList<>() : array.toJavaList(String.class);
    }

    private WebhookSubscriptionDTO toDTO(SystemWebhookSubscription entity) {
        WebhookSubscriptionDTO dto = new WebhookSubscriptionDTO();
        dto.setId(entity.getId());
        dto.setAppId(entity.getAppId());
        dto.setSubscriptionName(entity.getSubscriptionName());
        dto.setUrl(entity.getUrl());
        dto.setHttpMethod(entity.getHttpMethod());
        dto.setSecretPrefix(entity.getSecretPrefix());
        dto.setSecretLast4(entity.getSecretLast4());
        dto.setEvents(parseEvents(entity.getEvents()));
        dto.setTableCode(entity.getTableCode());
        dto.setHeaders(entity.getHeaders());
        dto.setSslVerify(entity.getSslVerify());
        dto.setTimeoutMs(entity.getTimeoutMs());
        dto.setMaxRetry(entity.getMaxRetry());
        dto.setStatus(entity.getStatus());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setRemark(entity.getRemark());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedTime(entity.getUpdatedTime());
        return dto;
    }
}
