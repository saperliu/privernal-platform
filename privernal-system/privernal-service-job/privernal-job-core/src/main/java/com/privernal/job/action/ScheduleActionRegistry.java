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

package com.privernal.job.action;

import com.privernal.job.api.action.ScheduleAction;
import com.privernal.job.api.action.ScheduleActionHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 动作注册表：actionType → handler；BEAN 动作白名单（@ScheduleAction 注解扫描，§8.1）。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class ScheduleActionRegistry {

    private final Map<String, ScheduleActionHandler> handlers = new HashMap<>();

    /** BEAN 动作白名单：@ScheduleAction 的 value → handler */
    private final Map<String, ScheduleActionHandler> beanActions = new HashMap<>();

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        // 先按定义名（不初始化）盘点，再逐个显式 getBean：getBeansOfType 会把
        // "创建中被依赖链反向引用"或初始化失败的 handler 静默吞掉，逐个取才能暴露真因
        String[] names = applicationContext.getBeanNamesForType(ScheduleActionHandler.class, true, false);
        log.info("[schedule] handler bean definitions: {}", String.join(",", names));
        for (String name : names) {
            try {
                ScheduleActionHandler handler = applicationContext.getBean(name, ScheduleActionHandler.class);
                ScheduleAction annotation = handler.getClass().getAnnotation(ScheduleAction.class);
                if (annotation != null) {
                    // 白名单实现（@ScheduleAction）只进 beanActions：多个实现的 actionType 同为
                    // BEAN，若都写 handlers 会互相覆盖成"所有 BEAN 任务执行最后一个注册的"
                    beanActions.put(annotation.value(), handler);
                } else {
                    handlers.put(handler.actionType(), handler);
                }
            } catch (Exception e) {
                log.error("[schedule] action handler init failed: {}", name, e);
            }
        }
        log.info("[schedule] action handlers: {} ; bean whitelist: {}", handlers.keySet(), beanActions.keySet());
    }

    public ScheduleActionHandler byActionType(String actionType) {
        return handlers.get(actionType);
    }

    /** BEAN 动作按白名单名取 handler；不在白名单返回 null（拒绝执行） */
    public ScheduleActionHandler byBeanName(String beanName) {
        return beanActions.get(beanName);
    }
}
