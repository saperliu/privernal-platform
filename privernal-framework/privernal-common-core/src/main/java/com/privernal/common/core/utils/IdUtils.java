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

package com.privernal.common.core.utils;

import cn.hutool.core.util.IdUtil;

/**
 * ID 生成。
 *
 * <p>业务主键由 MyBatis-Plus 的雪花 ID 与 warm-flow 的 SnowId19 负责，
 * 这里只提供验证码等临时键名需要的随机串。</p>
 *
 * @author saperliu
 */
public class IdUtils
{
    private IdUtils()
    {
    }

    /**
     * 32 位小写十六进制、不带横线的随机 UUID。
     */
    public static String simpleUUID()
    {
        return IdUtil.simpleUUID();
    }
}
