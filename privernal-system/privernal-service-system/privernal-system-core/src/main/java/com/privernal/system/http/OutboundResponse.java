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

/**
 * 公共 HTTP 出站响应。
 *
 * @author saperliu
 */
@Data
public class OutboundResponse {

    /** 请求是否发出并收到响应（false=传输层异常，error 有值） */
    private boolean executed;

    /** HTTP 状态码（未收到响应为 -1） */
    private int statusCode = -1;

    /** 响应体（调用方自行截断留存） */
    private String body;

    /** 耗时毫秒 */
    private long costMs;

    /** 传输层异常信息 */
    private String error;

    /** 2xx */
    private boolean success;

    /** 4xx（除 408/429）= 对方明确拒绝，重试无意义 */
    private boolean permanentFailure;
}
