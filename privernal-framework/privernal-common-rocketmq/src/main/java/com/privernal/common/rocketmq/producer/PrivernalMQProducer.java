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

package com.privernal.common.rocketmq.producer;

import com.privernal.common.core.utils.StringUtils;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2024-03-04 14:12
 * @Description 生产者的实例
 * <p>
 * 使用示例：
 * WorkflowMessage workflowMessage = new WorkflowMessage();
 * workflowMessage.setBusinessKey("170000000");
 * workflowMessage.setOccurrenceTime(new Date());
 * perfectMQProducer.sendMessage(wfsEventTopic, WorkflowTopicConstants.WORKFLOW_EVENT_TYPE_ADJUST, workflowMessage);
 */
@Slf4j
@Component
public class PrivernalMQProducer {

    @Value("${rocketmq.producer.send-message-timeout}")
    private Integer messageTimeOut;


    // 直接注入使用，用于发送消息到broker服务器
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 普通发送（这里的参数对象WorkflowMessage可以随意定义，可以发送个对象，也可以是字符串等）
     */
    public void sendMessage(String topic, String tag, Object payload) {
        Assert.hasLength(topic, "topic不能为空");
        if (StringUtils.isNotEmpty(tag)) {
            topic = topic + ":" + tag;
        }
        rocketMQTemplate.send(topic, MessageBuilder.withPayload(payload).build());
    }

    /**
     * 发送同步消息（阻塞当前线程，等待broker响应发送结果，这样不太容易丢失消息）
     * （msgBody也可以是对象，sendResult为返回的发送结果）
     */
    public SendResult sendSyncMessage(String topic, String tag, Object payload) {
        Assert.hasLength(topic, "topic不能为空");
        if (StringUtils.isNotEmpty(tag)) {
            topic = topic + ":" + tag;
        }
        SendResult sendResult = rocketMQTemplate.syncSend(topic, MessageBuilder.withPayload(payload).build());
        log.info("【sendMsg】sendResult={}", JSON.toJSONString(sendResult));
        return sendResult;
    }

    /**
     * 发送异步消息（通过线程池执行发送到broker的消息任务，执行完后回调：在SendCallback中可处理相关成功失败时的逻辑）
     * （适合对响应时间敏感的业务场景）
     */
    public void sendAsyncMessage(String topic, String tag, Object payload, SendCallback sendCallback) {
        Assert.hasLength(topic, "topic不能为空");
        if (StringUtils.isNotEmpty(tag)) {
            topic = topic + ":" + tag;
        }
        rocketMQTemplate.asyncSend(topic, MessageBuilder.withPayload(payload).build(), sendCallback);
    }

    /**
     * 发送延时消息（上面的发送同步消息，delayLevel的值就为0，因为不延时）
     * 在start版本中 延时消息一共分为18个等级分别为：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     */
    public SendResult sendDelayMessage(String topic, String tag, Object payload, int delayLevel) {
        Assert.hasLength(topic, "topic不能为空");
        if (StringUtils.isNotEmpty(tag)) {
            topic = topic + ":" + tag;
        }
        return rocketMQTemplate.syncSend(topic, MessageBuilder.withPayload(payload).build(), messageTimeOut, delayLevel);
    }

    /**
     * 发送单向消息（只负责发送消息，不等待应答，不关心发送结果，如日志）
     */
    public void sendOneWayMessage(String topic, String tag, Object payload) {
        Assert.hasLength(topic, "topic不能为空");
        if (StringUtils.isNotEmpty(tag)) {
            topic = topic + ":" + tag;
        }
        rocketMQTemplate.sendOneWay(topic, MessageBuilder.withPayload(payload).build());
    }

    /**
     * 发送顺序消息（hashKey的值为唯一）
     *
     * @param topic   主题
     * @param tag     标识
     * @param payload 内容
     * @param hashKey 唯一KEY
     **/
    public SendResult sendSyncOrderlyMessage(String topic, String tag, Object payload, String hashKey) {
        Assert.hasLength(topic, "topic不能为空");
        if (StringUtils.isNotEmpty(tag)) {
            topic = topic + ":" + tag;
        }
        return rocketMQTemplate.syncSendOrderly(topic, MessageBuilder.withPayload(payload).build(), hashKey, messageTimeOut);
    }
}
