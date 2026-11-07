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

package com.privernal.common.protection.service;

import org.redisson.api.RBlockingDeque;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2022-01-15 15:50
 * @Description
 */
@SuppressWarnings(value = {"unchecked", "rawtypes"})
@Component
public class RedisDelayedQueueService {

    @Autowired
    RedissonClient redissonClient;

    private static Logger logger = LoggerFactory.getLogger(RedisDelayedQueueService.class);

    /**
     * 添加队列
     *
     * @param t        DTO传输类
     * @param delay    时间数量
     * @param timeUnit 时间单位
     * @param <T>      泛型
     */
    public <T> void addQueue(T t, long delay, TimeUnit timeUnit, String queueName) {
        logger.info("添加队列{},delay:{},timeUnit:{}", queueName, delay, timeUnit);
        RBlockingQueue<T> blockingFairQueue = redissonClient.getBlockingQueue(queueName);

        RDelayedQueue<T> delayedQueue = redissonClient.getDelayedQueue(blockingFairQueue);
        delayedQueue.offer(t, delay, timeUnit);
    }

    /**
     * 获取延迟队列
     *
     * @param queueCode
     * @param <T>
     * @return
     * @throws InterruptedException
     */
    public <T> T getDelayQueue(String queueCode) throws InterruptedException {
        RBlockingDeque<T> blockingDeque = redissonClient.getBlockingDeque(queueCode);
        T value = (T) blockingDeque.take();
        return value;
    }

}
