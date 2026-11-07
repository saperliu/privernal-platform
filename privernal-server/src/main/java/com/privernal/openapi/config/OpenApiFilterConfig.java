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

package com.privernal.openapi.config;

import com.privernal.common.redis.service.RedisService;
import com.privernal.openapi.filter.OpenApiAuthFilter;
import com.privernal.system.service.OpenApiCallLogService;
import com.privernal.system.service.OpenApiCredentialService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 把 OpenApiAuthFilter 限定在 /openapi/* 上，不进 sa-token 的鉴权分支。
 */
@Configuration
public class OpenApiFilterConfig {

    @Bean
    public FilterRegistrationBean<OpenApiAuthFilter> openApiAuthFilterRegistration(
            OpenApiCredentialService credentialService,
            OpenApiCallLogService callLogService,
            RedisService redisService) {
        FilterRegistrationBean<OpenApiAuthFilter> registration =
                new FilterRegistrationBean<>(new OpenApiAuthFilter(credentialService, callLogService, redisService));
        registration.addUrlPatterns("/openapi/*");
        registration.setName("openApiAuthFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
