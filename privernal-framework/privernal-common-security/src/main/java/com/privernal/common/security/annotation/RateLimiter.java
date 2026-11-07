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

package com.privernal.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（Redis 固定窗口，原子 INCR+EXPIRE）
 *
 * 接入层限流是 SECURITY.md「部署者必须处理的安全项」的代码侧兜底：
 * 网关限流仍是强制项，本注解用于网关之外的进程内防线（认证/短信/外链等敏感端点）。
 *
 * @author saperliu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {

    /**
     * 限流键自定义后缀；缺省用「类名.方法名」，与限流维度拼成完整 Redis key
     */
    String key() default "";

    /**
     * 时间窗口内允许的最大请求数
     */
    int max() default 10;

    /**
     * 时间窗口长度（秒）
     */
    int period() default 60;

    /**
     * 限流维度：按客户端 IP 限流（缺省）/ 全局共享一个窗口
     */
    LimitType type() default LimitType.IP;

    enum LimitType {
        /** 按客户端 IP 限流 */
        IP,
        /** 全局共享窗口（适合总量闸门类端点） */
        GLOBAL
    }
}
