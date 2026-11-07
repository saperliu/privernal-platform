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
 * 权限不足：{@code @PreAuthorize} 切面校验 hasPermi 不通过时抛出，
 * 由全局异常处理器映射为 403 类响应。
 *
 * @author saperliu
 */
public class PreAuthorizeException extends RuntimeException {

    private static final long serialVersionUID = 1L;
}
