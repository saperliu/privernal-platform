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

package com.privernal.common.rocketmq.wheel;

import com.privernal.common.rocketmq.constant.RocketMQConstants;
import com.privernal.common.rocketmq.producer.DelayMqProducer;
import com.alibaba.fastjson2.JSON;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import java.util.Date;
import java.util.concurrent.CountDownLatch;

/**
 * @author saperliu
 * @version 1.0
 * @date 2022/01/19 11:39
 * @return
 */
@Slf4j
public class SendRealMqTask implements TimerTask {

    private DelayMqProducer delayMqProducer;

    private Message         message;

    private CountDownLatch  countDownLatch;

    private SendResult      sendResult;

    @Override
    public void run(Timeout timeout) throws Exception {
        validate();
        toRealMessage(message);
        sendResult = delayMqProducer.send(message);
        String uuid = message.getUserProperty(RocketMQConstants.ORIGINAL_UUID);
        log.info("真实Mq ---uuId : {}在当前时间{} 发送成功，返回值 {}", uuid, String.format("%tF %<tT", new Date()), JSON.toJSONString(sendResult));
        countDownLatch.countDown();
    }

    public SendResult getResult() {
        return sendResult;
    }

    private void validate() {
        if (delayMqProducer == null) {
            throw new IllegalArgumentException("producer is  null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message is  null");
        }
        if (countDownLatch == null) {
            throw new IllegalArgumentException("countDownLatch is null");
        }
    }

    private void toRealMessage(Message message) {
        String topic = message.getProperty(RocketMQConstants.ORIGINAL_TOPIC);
        if (StringUtils.isNotBlank(topic)) {
            message.setTopic(topic);
        }
        String tag = message.getProperty(RocketMQConstants.ORIGINAL_TAG);
        if (StringUtils.isNotBlank(tag)) {
            message.setTags(tag);
        }
        String key = message.getProperty(RocketMQConstants.ORIGINAL_KEY);
        if (StringUtils.isNotBlank(key)) {
            message.setKeys(key);
        }
    }

    public DelayMqProducer getDelayMqProducer() {
        return delayMqProducer;
    }

    public void setDelayMqProducer(DelayMqProducer delayMqProducer) {
        this.delayMqProducer = delayMqProducer;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    public void setCountDownLatch(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }
}
