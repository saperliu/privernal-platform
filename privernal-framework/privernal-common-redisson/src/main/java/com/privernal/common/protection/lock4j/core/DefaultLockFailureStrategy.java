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

package com.privernal.common.protection.lock4j.core;

import com.privernal.common.core.exception.PrivernalError;
import com.privernal.common.core.exception.ServiceException;
import com.baomidou.lock.LockFailureStrategy;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 自定义获取锁失败策略，抛出 {@link ServiceException} 异常
 */
@Slf4j
public class DefaultLockFailureStrategy implements LockFailureStrategy {

    @Override
    public void onLockFailure(String key, Method method, Object[] arguments) {
        log.debug("[onLockFailure][线程:{} 获取锁失败，key:{} 获取失败:{} ]", Thread.currentThread().getName(), key, arguments);
        throw new ServiceException(new PrivernalError(1002, "请求失败，请稍后重试"));
    }
}
