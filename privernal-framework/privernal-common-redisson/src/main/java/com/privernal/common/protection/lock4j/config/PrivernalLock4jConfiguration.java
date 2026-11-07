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

package com.privernal.common.protection.lock4j.config;

import com.privernal.common.protection.lock4j.core.DefaultLockFailureStrategy;
import com.privernal.common.protection.lock4j.core.Lock4jRedisKeyConstants;
import cn.hutool.core.util.ClassUtil;
import com.baomidou.lock.spring.boot.autoconfigure.LockAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = LockAutoConfiguration.class)
public class PrivernalLock4jConfiguration {

    static {
        // 手动加载 Lock4jRedisKeyConstants 类，因为它不会被使用到
        // 如果不加载，会导致 Redis 监控，看到它的 Redis Key 枚举
        ClassUtil.loadClass(Lock4jRedisKeyConstants.class.getName());
    }

    @Bean
    public DefaultLockFailureStrategy lockFailureStrategy() {
        return new DefaultLockFailureStrategy();
    }

}
