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

package com.privernal.common.core.exception;

/**
 * 验证码校验失败（登录/敏感操作的图形或短信验证码不匹配、过期）。
 * 由全局异常处理器翻译为「请重新输入验证码」类提示。
 *
 * @author saperliu
 */
public class CaptchaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CaptchaException(String msg) {
        super(msg);
    }
}
