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

package com.privernal.common.rocketmq.listener;

import cn.hutool.core.date.DateUtil;
import com.privernal.common.rocketmq.constant.RocketMQConstants;
import com.privernal.common.rocketmq.producer.DelayMqProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author saperliu
 * @version 1.0
 * @date 2022/01/07 14:51
 * @return
 */
@Slf4j
public class DelayMessageListener implements MessageListenerConcurrently {

    private DelayMqProducer delayMqProducer;

    public DelayMessageListener(DelayMqProducer delayMqProducer) {
        this.delayMqProducer = delayMqProducer;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        try {
            for (MessageExt messageExt : msgs) {
                Map<String, String> properties = messageExt.getProperties();

                String topic = properties.get(RocketMQConstants.ORIGINAL_TOPIC);
                String times = properties.get(RocketMQConstants.DELAY_TIMES);
                String tag = properties.get(RocketMQConstants.ORIGINAL_TAG);
                String keys = properties.get(RocketMQConstants.ORIGINAL_KEY);
                String uuid = properties.get(RocketMQConstants.ORIGINAL_UUID);
                if (StringUtils.isBlank(topic)) {
                    continue;
                }
                if (StringUtils.isBlank(times)) {
                    log.error("该延时消息未收到延时时间");
                    continue;
                }
                properties.remove(RocketMQConstants.DELAY_TIMES);
                log.info("延时消息:uuId {}  topic: {} time :{} tags: {} #####body:{}", uuid, messageExt.getTopic(), DateUtil.date(Long.valueOf(times) * 1000), messageExt.getTags(), new String(messageExt.getBody()));
                Message message = new Message();
                message.setTopic(topic);
                if (StringUtils.isNotBlank(tag)) {
                    message.setTags(tag);
                }
                if (StringUtils.isNotBlank(keys)) {
                    message.setKeys(keys);
                }
                if (StringUtils.isNotBlank(uuid)) {
                    message.putUserProperty(RocketMQConstants.ORIGINAL_UUID, uuid);
                }
                message.setBody(messageExt.getBody());
                delayMqProducer.sendDelay(message, new Date(Long.valueOf(times) * 1000L));
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        } catch (Exception e) {
            log.error("消息发送失败", e);
        }
        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
    }
}
