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

package com.privernal.ai.config;

import com.privernal.ai.filter.AiSessionGuardFilter;
import com.privernal.system.service.SystemAiSessionLogService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * AI 会话守门 Filter 注册（后端唯一需要动的一处）。
 * <p>
 * 挂载硬约束：
 * ① 显式 order = LOWEST_PRECEDENCE - 100——落在 CorsFilter（Integer.MIN_VALUE）与
 * RequestContextFilter（-105，绑定 RequestContextHolder，晚于它 SecurityUtils 才能解析
 * sa-token 会话填审计身份）之后、sa-token 的 SaServletFilter（裸 @Bean 默认 LOWEST，
 * 顺序不可依赖）之前；
 * ② OpenApiAuthFilter（HIGHEST_PRECEDENCE+20）只挂 /openapi/*，与本 Filter 的 urlPatterns 不重叠；
 * ③ urlPatterns 覆盖 /back/* 与 /dbs/*（动态表数据读写走 /dbs/open/dynamic/data/*）。
 */
@Configuration
public class AiSessionFilterConfig {

    @Bean
    public FilterRegistrationBean<AiSessionGuardFilter> aiSessionGuardFilterRegistration(
            SystemAiSessionLogService sessionLogService) {
        FilterRegistrationBean<AiSessionGuardFilter> registration =
                new FilterRegistrationBean<>(new AiSessionGuardFilter(sessionLogService));
        registration.addUrlPatterns("/back/*", "/dbs/*");
        registration.setName("aiSessionGuardFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        return registration;
    }
}
