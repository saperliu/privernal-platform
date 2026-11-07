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

package com.privernal.gateway.filter;

import org.springframework.context.annotation.Configuration;

/**
 * 鉴权过滤器已收口，本类不再注册任何 Bean。
 * <p>
 * 原先这里有一个与 privernal-common-security IdTokenConfigure 同名的 @Bean getSaServletFilter，
 * 靠 allow-bean-definition-overriding 决出生效者，扫描顺序一变就静默换人；
 * 而其 appKey/sign 分支存在三重断裂：验签通过后不建立任何身份、签名参数不含请求体、
 * signVerify 又跳过时间戳窗口，加上 Redis 里没有任何 appKey 写入方 —— 开放接口因此跑不通。
 * <p>
 * 现在：登录态鉴权只有 IdTokenConfigure 一处（安全响应头已搬过去）；
 * 机器调用走 /openapi/** 的独立验签过滤器，不再挤进 sa-token 的 auth 分支。
 * com.privernal.gateway 下的 sign 包与 DefaultSignService 随之失去调用方，确认无外部引用后可一并删除。
 */
@Configuration
public class GatewayTokenFilter {
}
