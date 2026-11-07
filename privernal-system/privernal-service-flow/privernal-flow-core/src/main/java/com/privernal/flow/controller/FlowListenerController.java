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

package com.privernal.flow.controller;

import com.privernal.common.web.domain.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.Listener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程监听器Controller
 * <p>
 * 提供设计器「监听器」配置页的可选监听器清单：
 * 1. Spring 容器中 warm-flow 引擎监听器（实现 {@link Listener} / {@link GlobalListener}），
 * 类路径可直接配置到节点的 listenerPath；
 * 2. 平台内置 Spring 事件监听（自动生效，列出仅供查看）。
 * </p>
 *
 * @author saperliu
 */
@Tag(name = "流程监听器相关接口")
@RestController
@RequestMapping("/back/flow/listener")
public class FlowListenerController {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 监听器列表（设计器监听器配置下拉数据源）
     */
    @Operation(summary = "查询可用监听器列表")
    @GetMapping("/list")
    public CommonResult<List<Map<String, Object>>> list() {
        List<Map<String, Object>> listeners = new ArrayList<>();
        // warm-flow 引擎监听器 Bean：类路径可配置到节点 listenerType/listenerPath
        Map<String, Listener> engineListeners = applicationContext.getBeansOfType(Listener.class);
        engineListeners.forEach((beanName, listener) -> {
            if (listener instanceof GlobalListener) {
                return;
            }
            listeners.add(buildListener(beanName, listener.getClass().getName(), "create",
                    "warm-flow 引擎监听器：" + listener.getClass().getSimpleName()));
        });
        Map<String, GlobalListener> globalListeners = applicationContext.getBeansOfType(GlobalListener.class);
        globalListeners.forEach((beanName, listener) -> listeners.add(buildListener(beanName,
                listener.getClass().getName(), "create",
                "warm-flow 全局监听器（自动生效）：" + listener.getClass().getSimpleName())));
        // 平台内置 Spring 事件监听（随流程操作自动触发，无需配置）
        listeners.add(buildListener("flowEventListener",
                "com.privernal.flow.listener.FlowEventListener", "create", "平台流程事件日志（自动生效）"));
        listeners.add(buildListener("flowMessageNotifyListener",
                "com.privernal.flow.notice.FlowMessageNotifyListener", "create", "审批消息通知（自动生效）"));
        return CommonResult.ok(listeners);
    }

    private Map<String, Object> buildListener(String id, String path, String type, String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", description);
        item.put("path", path);
        item.put("type", type);
        item.put("description", description);
        return item;
    }
}
