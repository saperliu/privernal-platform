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

import com.privernal.common.rocketmq.calculate.DelayLevelCalculate;
import com.privernal.common.rocketmq.constant.RocketMQConstants;
import com.privernal.common.rocketmq.listener.DelayMessageListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * @author saperliu
 * @version 1.0
 * @date 2022/01/17 12:12
 * @return
 */
@Slf4j
public class DelayMqProducer extends DefaultMQProducer {

    private DefaultMQPushConsumer defaultMQPushConsumer;

    public DelayMqProducer(String producerGroup) {
        super(producerGroup);
    }

    /**
     * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h rocketMQ自动支持18个级别 等级全部转化为秒
     * <p>
     * 延时小于1分钟的 不支持，可以设置强制参数来支持，但是会有误差
     */
    public SendResult sendDelay(Message msg, Date planSendTime) throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        if (planSendTime == null) {
            return super.send(msg);
        }
        long l = Duration.between(Instant.now(), planSendTime.toInstant()).getSeconds();
        //如果不等于0,说明设置了延时等级，直接用rocketMQ支持的发送
        if (l <= RocketMQConstants.TIME_OUT) {
//            HashedWheelTimer instance = TimeWheelFactory.getInstance();
//            CountDownLatch countDownLatch = new CountDownLatch(1);
//            SendRealMqTask sendRealMqTask = new SendRealMqTask();
//            sendRealMqTask.setDelayMqProducer(this);
//            sendRealMqTask.setMessage(msg);
//            sendRealMqTask.setCountDownLatch(countDownLatch);
//            instance.newTimeout(sendRealMqTask, l < 0 ? 1 : l, TimeUnit.SECONDS);
//            countDownLatch.await();
//            return sendRealMqTask.getResult();
            // 这样处理时间不准确，但可以保证消息不丢失. 上面的处理方案重启时会丢1分钟内的数据
            Integer level = DelayLevelCalculate.calculateDefault(l);
            msg.setDelayTimeLevel(level);
            log.info(" if topic : {} uuid: {} 消息当前时间为{},消息计划发送时间{},延时等级本次建议为{}", msg.getTopic(), msg.getUserProperty(RocketMQConstants.ORIGINAL_UUID), String.format("%tF %<tT", new Date()), String.format("%tF %<tT", planSendTime), level);
            return super.send(msg);
        } else {
            Integer level = DelayLevelCalculate.calculateDefault(l);
            fillMessage(msg, level, planSendTime);
            return super.send(msg);
        }
    }

    @Override
    public void start() throws MQClientException {
        super.start();
        initConsumer();
    }

    private void initConsumer() throws MQClientException {
        defaultMQPushConsumer = new DefaultMQPushConsumer(super.getProducerGroup());
        defaultMQPushConsumer.setNamesrvAddr(super.getNamesrvAddr());
        defaultMQPushConsumer.setMessageListener(new DelayMessageListener(this));
        defaultMQPushConsumer.subscribe(RocketMQConstants.PROXY_TOPIC, "*");
        defaultMQPushConsumer.start();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (defaultMQPushConsumer != null) {
            defaultMQPushConsumer.shutdown();
        }
    }

    /**
     * 填充消息
     *
     * @param msg          消息体
     * @param level        延时等级
     * @param planSendTime 发送时间
     */
    private void fillMessage(Message msg, Integer level, Date planSendTime) {
        // 下次发送时间
        long time = planSendTime.getTime() / 1000;
        msg.putUserProperty(RocketMQConstants.DELAY_TIMES, String.valueOf(time));

        String topic = msg.getProperty(RocketMQConstants.ORIGINAL_TOPIC);
        if (StringUtils.isBlank(topic)) {
            msg.putUserProperty(RocketMQConstants.ORIGINAL_TOPIC, msg.getTopic());
        }
        String tag = msg.getProperty(RocketMQConstants.ORIGINAL_TAG);
        if (StringUtils.isBlank(tag)) {
            if (StringUtils.isNotBlank(msg.getTags())) {
                msg.putUserProperty(RocketMQConstants.ORIGINAL_TAG, msg.getTags());
            }
        }
        String keys = msg.getProperty(RocketMQConstants.ORIGINAL_KEY);
        if (StringUtils.isBlank(keys)) {
            if (StringUtils.isNotBlank(msg.getKeys())) {
                msg.putUserProperty(RocketMQConstants.ORIGINAL_KEY, msg.getKeys());
            }
        }

        String property = msg.getProperty(RocketMQConstants.ORIGINAL_UUID);
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        if (StringUtils.isBlank(property)) {
            msg.putUserProperty(RocketMQConstants.ORIGINAL_UUID, uuid);
        }
        msg.setDelayTimeLevel(level);
        msg.setTopic(RocketMQConstants.PROXY_TOPIC);
        log.info("fillMessage消息uuid {} 消息当前时间为{},消息计划发送时间{},time:{}延时等级本次建议为{}", uuid, String.format("%tF %<tT", new Date()), String.format("%tF %<tT", planSendTime), time, level);
    }
}
