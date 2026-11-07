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


import com.alibaba.fastjson2.JSON;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;
/**
 * @author saperliu
 * @version 1.0.0
 * @date 2022-01-15 15:46
 * @Description 初始化队列监听
 */
@Component
public class RedisDelayedQueueInit implements ApplicationContextAware {

    private static Logger logger = LoggerFactory.getLogger(RedisDelayedQueueInit.class);

    @Autowired
    RedissonClient redissonClient;

    /**
     * 获取应用上下文并获取相应的接口实现类
     *
     * @param applicationContext
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, RedisDelayedQueueListener> map = applicationContext.getBeansOfType(RedisDelayedQueueListener.class);
        for (Map.Entry<String, RedisDelayedQueueListener> taskEventListenerEntry : map.entrySet()) {
            String listenerName = taskEventListenerEntry.getValue().getQueueName();
            startThread(listenerName, taskEventListenerEntry.getValue());
        }
    }

    /**
     * 启动线程获取队列*
     *
     * @param queueName                 queueName
     * @param redisDelayedQueueListener 任务回调监听
     * @param <T>                       泛型
     * @return
     */
    private <T> void startThread(String queueName, RedisDelayedQueueListener redisDelayedQueueListener) {
        RBlockingQueue<T> blockingFairQueue = redissonClient.getBlockingQueue(queueName);
        //由于此线程需要常驻，可以新建线程，不用交给线程池管理
        Thread thread = new Thread(() -> {
            logger.info("启动监听队列线程" + queueName);
            while (true) {
                try {
                    T t = blockingFairQueue.take();
                    logger.info("监听队列线程{},获取到值:{}", queueName, JSON.toJSONString(t));
                    new Thread(() -> {
                        redisDelayedQueueListener.invoke(t);
                    }).start();
                } catch (Exception e) {
                    logger.error("监听队列线程错误,", e);
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        });
        thread.setName(queueName);
        thread.start();
    }

}
