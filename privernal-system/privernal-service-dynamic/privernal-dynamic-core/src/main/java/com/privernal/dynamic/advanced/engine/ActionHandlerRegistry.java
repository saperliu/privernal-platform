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

import com.privernal.common.core.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作处理器注册表：按 type 收集所有 {@link LinkageActionHandler} Spring bean。
 * ObjectProvider 惰性解析——处理器依赖动态表服务，动态表服务又依赖联动分发，
 * 直接注入 List 会形成环。
 */
@Slf4j
@Component
public class ActionHandlerRegistry {

    @Autowired
    private ObjectProvider<LinkageActionHandler> handlersProvider;

    private volatile Map<String, LinkageActionHandler> registry;

    public LinkageActionHandler get(String type) {
        if (StringUtils.isEmpty(type)) {
            return null;
        }
        return handlers().get(type);
    }

    public List<String> supportedTypes() {
        return List.copyOf(handlers().keySet());
    }

    private Map<String, LinkageActionHandler> handlers() {
        Map<String, LinkageActionHandler> current = registry;
        if (current == null) {
            synchronized (this) {
                current = registry;
                if (current == null) {
                    current = new HashMap<>();
                    for (LinkageActionHandler handler : handlersProvider) {
                        current.put(handler.type(), handler);
                    }
                    registry = current;
                }
            }
        }
        return current;
    }
}
