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

package com.privernal.common.rocketmq.configure;


import com.privernal.common.rocketmq.producer.DelayMqProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2022-01-12 14:01
 * @Description 创建消息的生产者
 */
@Slf4j
@Configuration
public class RocketMqConfig {

    @Value("${rocketmq.name-server}")
    private String rocketmqServer;

    @Value("${rocketmq.producer.group}")
    private String producerGroup;

    @Value("${rocketmq.producer.retry-next-server}")
    private boolean retryServer;

    @Value("${rocketmq.producer.retry-times-when-send-failed}")
    private int retryTimes;

    @Value("${rocketmq.producer.send-message-timeout}")
    private int timeOut;

    @Value("${rocketmq.producer.queue-nums}")
    private int queueNums;


    @Bean
    public DefaultMQProducer defaultMQProducer() {
        DelayMqProducer producer;
        producer = new DelayMqProducer(producerGroup);
        producer.setUnitName("privernal");
        producer.setRetryTimesWhenSendAsyncFailed(retryTimes);
        producer.setRetryTimesWhenSendFailed(retryTimes);
        producer.setSendMsgTimeout(timeOut);
        producer.setNamesrvAddr(rocketmqServer);
        //不开启vip通道 开通口端口会减2
        producer.setVipChannelEnabled(false);
        producer.setDefaultTopicQueueNums(queueNums);
        log.info(" producerGroup   {}  {} {}", producerGroup, rocketmqServer, queueNums);
        return producer;
    }
}
