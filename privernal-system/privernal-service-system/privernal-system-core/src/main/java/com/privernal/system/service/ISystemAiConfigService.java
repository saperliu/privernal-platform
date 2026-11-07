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

import com.privernal.system.domain.SystemAiConfig;

import java.util.Map;

/**
 * AI 模型配置服务（公网模型口径）。
 */
public interface ISystemAiConfigService {

    /** 当前组织的配置（apiKey 只回掩码）；未配置返回带 notice 的空壳 */
    Map<String, Object> currentWithNotice();

    /** 保存（upsert）：apiKey 非空重新加密、空保持不变；baseUrl 必填；密文能力不可用时显式报错不降级 */
    SystemAiConfig save(SystemAiConfig patch, String rawApiKey);

    /** 启停本组织 AI 能力 */
    void changeEnabled(String enabled);

    /** 取当前组织生效配置（含解密后的 apiKey，仅供运行时调用方使用；管理接口不得暴露） */
    SystemAiConfig effectiveConfig();
}
