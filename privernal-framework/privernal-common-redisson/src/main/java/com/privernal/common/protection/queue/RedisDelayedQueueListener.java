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

package com.privernal.common.protection.queue;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2022-01-15 15:49
 * @Description 队列 监听器
 */

public interface RedisDelayedQueueListener<T> {
    /**
     * 执行方法
     *
     * @param t
     */
    void invoke(T t);

    /**
     * 获取队列的名称
     * @return 名称
     */
    String  getQueueName();
}
