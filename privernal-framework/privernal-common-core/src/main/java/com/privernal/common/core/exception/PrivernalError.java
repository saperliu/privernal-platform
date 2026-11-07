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

import lombok.Data;

import java.io.Serializable;

/**
 * 全局错误码封装
 *
 * @author saperliu
 * @date 2023-02-15
 */
@Data
public class PrivernalError implements Serializable {

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误提示
     */
    private final String message;
}
