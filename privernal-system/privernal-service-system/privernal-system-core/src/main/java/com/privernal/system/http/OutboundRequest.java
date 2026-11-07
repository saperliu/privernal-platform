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

package com.privernal.system.http;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公共 HTTP 出站请求（HttpOutboundClient）。
 *
 * @author saperliu
 */
@Data
public class OutboundRequest {

    private String url;

    /** 默认 POST */
    private String method = "POST";

    /** 请求体（原样发送） */
    private String body;

    /** 自定义头（保留头由调用方自己保证） */
    private Map<String, String> headers = new LinkedHashMap<>();

    /** 请求超时毫秒 */
    private int timeoutMs = 5000;

    /** true=信任所有证书（内网自签名场景）；false=标准校验 */
    private boolean trustAllSsl = false;
}
