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

import com.privernal.common.core.utils.StringUtils;
import com.alibaba.fastjson2.JSON;

/**
 * 业务服务异常：携带可选的 {@code module}（出错模块）与 {@code code}（业务错误码），
 * 交给全局异常处理器组装响应。
 *
 * <p>{@code message} 字段刻意覆盖 {@link RuntimeException#getMessage()}：
 * 调用方抛异常时填的是「给人看」的文案，与父类持有的 cause 链解耦。</p>
 *
 * @author saperliu
 */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 出错模块，可为空 */
    private String module;

    /** 业务错误码，可为空 */
    private String code;

    /** 展示给调用方的文案 */
    private String message;

    public ServiceException(String message) {
        this.message = message;
    }

    public ServiceException(String message, String code) {
        this.message = message;
        this.code = code;
    }

    /**
     * 模块级异常：变参按约定序列化为 JSON 数组作为文案（历史契约，调用方按此解析）。
     */
    public ServiceException(String module, String code, Object... args) {
        this.module = module;
        this.code = code;
        this.message = JSON.toJSONString(args);
    }

    public ServiceException(PrivernalError error) {
        this(error.getMessage(), error.getCode().toString());
    }

    public ServiceException(PrivernalError error, Object... args) {
        this(StringUtils.format(error.getMessage(), args), error.getCode().toString());
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }
}
