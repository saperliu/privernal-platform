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

package com.privernal.common.security.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.same.SaSameUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.web.domain.CommonResult;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2021-08-30 11:48
 * @Description 网关访问，带有id-token,每个微服务检验id-token
 */
@Configuration
public class IdTokenConfigure implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(IdTokenConfigure.class);

    @Autowired
    IgnoreWhitePath ignoreWhitePath;

    /**
     * 全局过滤器，校验是否从网关过来的访问，否则拒绝
     *
     * @return
     */
    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
                .addInclude("/**")
                .setAuth(obj -> {
                    // 校验 Id-Token 身份凭证     —— 以下两句代码可简化为：SaIdUtil.checkCurrentRequestToken();
                    String token = SaHolder.getRequest().getHeader(SaSameUtil.SAME_TOKEN);
                    log.debug("IdTokenConfigure 命中拦截链，白名单={}", ignoreWhitePath.getWhites());
                    log.debug("request url={}", SaHolder.getRequest().getUrl());
                    log.debug("request path={}", SaHolder.getRequest().getRequestPath());
                    log.debug("request paramKeys={}", SaHolder.getRequest().getParamMap().keySet());
                    // 通过网关的请求验证id_token
                    if (StringUtils.isNotEmpty(token)) {
                        SaSameUtil.checkToken(token);
                    } else {
                        // 不通过网关的请求,验证登录
                        SaRouter.notMatch(ignoreWhitePath.getWhites()).check(r -> StpUtil.checkLogin());

                    }
                })
                .setError(e -> {
                    // 设置响应头
                    log.error(" security id token configure ",e);
                    SaHolder.getResponse().setHeader("Content-Type", "application/json;charset=UTF-8");
                    return JSON.toJSONString(CommonResult.fail(CommonResult.AUTH_FAIL, e.getMessage()));
                })
                // 前置函数：认证之前统一加安全响应头。
                // 这组头原先在 privernal-server 的 GatewayTokenFilter 里，两个同名 @Bean 靠 bean 覆盖
                // 决出胜负、生效者不确定，现收口到本类作为唯一真源。
                .setBeforeAuth(r -> SaHolder.getResponse()
                        .setServer("privernal-server")
                        // iframe 嵌入策略
                        .setHeader("X-Frame-Options", "SAMEORIGIN")
                        .setHeader("X-XSS-Protection", "1; mode=block")
                        .setHeader("X-Content-Type-Options", "nosniff")
                        .setHeader("content-type", "application/json"));
    }


}
