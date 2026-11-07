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

package com.privernal.system.controller;

import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.constant.WebhookEventTypes;
import com.privernal.system.dto.WebhookDeliveryLogDTO;
import com.privernal.system.dto.WebhookEventDTO;
import com.privernal.system.dto.WebhookSubscriptionDTO;
import com.privernal.system.filter.WebhookEventFilterDTO;
import com.privernal.system.filter.WebhookSubscriptionFilterDTO;
import com.privernal.system.service.WebhookEventService;
import com.privernal.system.service.WebhookSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Webhook 订阅与事件投递的管理端接口
 */
@Tag(name = "Webhook订阅与事件")
@RestController
@Slf4j
public class SystemWebhookController extends BaseController {

    @Autowired
    private WebhookSubscriptionService subscriptionService;

    @Autowired
    private WebhookEventService eventService;

    @Operation(summary = "订阅列表")
    @PreAuthorize(hasPermi = "system:webhook:list")
    @PostMapping("/back/system/webhook/subscription/list")
    public CommonResult<PageListDTO<Object>> subscriptionList(@RequestBody WebhookSubscriptionFilterDTO filter) {
        startPage(filter);
        List<WebhookSubscriptionDTO> list = subscriptionService.selectList(filter);
        return getDataTable(list);
    }

    @Operation(summary = "订阅详情")
    @PreAuthorize(hasPermi = "system:webhook:query")
    @GetMapping("/back/system/webhook/subscription/{id}")
    public CommonResult<Object> subscriptionDetail(@PathVariable("id") String id) {
        return CommonResult.ok(subscriptionService.selectById(id));
    }

    /**
     * 新建订阅。返回体里的 secret 是唯一的明文出口，之后只能通过轮换获得新密钥
     */
    @Operation(summary = "新建订阅")
    @PreAuthorize(hasPermi = "system:webhook:add")
    @Log(title = "Webhook订阅", operationType = OperationType.INSERT)
    @PostMapping("/back/system/webhook/subscription/save")
    public CommonResult<Object> addSubscription(@RequestBody WebhookSubscriptionDTO dto) {
        return CommonResult.ok(subscriptionService.create(dto));
    }

    @Operation(summary = "修改订阅")
    @PreAuthorize(hasPermi = "system:webhook:edit")
    @Log(title = "Webhook订阅", operationType = OperationType.UPDATE)
    @PutMapping("/back/system/webhook/subscription/edit")
    public CommonResult<Object> editSubscription(@RequestBody WebhookSubscriptionDTO dto) {
        return CommonResult.ok(subscriptionService.update(dto));
    }

    @Operation(summary = "删除订阅")
    @PreAuthorize(hasPermi = "system:webhook:remove")
    @Log(title = "Webhook订阅", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/webhook/subscription/{ids}")
    public CommonResult<Object> removeSubscription(@PathVariable("ids") String[] ids) {
        return toAjax(subscriptionService.deleteByIds(ids));
    }

    @Operation(summary = "轮换订阅密钥")
    @PreAuthorize(hasPermi = "system:webhook:edit")
    @Log(title = "Webhook订阅密钥轮换", operationType = OperationType.UPDATE)
    @PutMapping("/back/system/webhook/subscription/rotate/{id}")
    public CommonResult<Object> rotateSubscription(@PathVariable("id") String id) {
        return CommonResult.ok(subscriptionService.rotateSecret(id));
    }

    @Operation(summary = "可订阅的事件类型")
    @PreAuthorize(hasPermi = "system:webhook:list")
    @GetMapping("/back/system/webhook/eventTypes")
    public CommonResult<Object> eventTypes() {
        return CommonResult.ok(WebhookEventTypes.all());
    }

    @Operation(summary = "事件列表")
    @PreAuthorize(hasPermi = "system:webhook:delivery")
    @PostMapping("/back/system/webhook/event/list")
    public CommonResult<PageListDTO<Object>> eventList(@RequestBody WebhookEventFilterDTO filter) {
        startPage(filter);
        List<WebhookEventDTO> list = eventService.selectList(filter);
        return getDataTable(list);
    }

    @Operation(summary = "事件详情（含投递记录）")
    @PreAuthorize(hasPermi = "system:webhook:delivery")
    @GetMapping("/back/system/webhook/event/{id}")
    public CommonResult<Object> eventDetail(@PathVariable("id") String id) {
        return CommonResult.ok(eventService.detail(id));
    }

    @Operation(summary = "重发事件")
    @PreAuthorize(hasPermi = "system:webhook:resend")
    @Log(title = "Webhook事件重发", operationType = OperationType.UPDATE)
    @PutMapping("/back/system/webhook/event/resend/{id}")
    public CommonResult<Object> resendEvent(@PathVariable("id") String id) {
        return CommonResult.ok(eventService.resend(id));
    }

    @Operation(summary = "事件的投递记录")
    @PreAuthorize(hasPermi = "system:webhook:delivery")
    @GetMapping("/back/system/webhook/delivery/{eventId}")
    public CommonResult<Object> deliveryLogs(@PathVariable("eventId") String eventId) {
        List<WebhookDeliveryLogDTO> logs = eventService.deliveryLogs(eventId);
        return CommonResult.ok(logs);
    }
}
