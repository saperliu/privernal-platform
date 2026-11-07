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

package com.privernal.message.socket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实时节拍中枢测试：开关、节拍钳制、到期与不补发、各类上限与回收。
 * <p>
 * 时钟走假值（覆盖 {@code nowMillis()}），所以整类用例不 sleep 一秒。
 *
 * @author saperliu
 */
@DisplayName("大屏实时节拍通道")
class SocketTickBrokerTest {

    /** 假时钟：用例自己拨时间 */
    private static final class Fake extends SocketTickBroker {

        private long now = 0;

        @Override
        long nowMillis() {
            return now;
        }

        void advance(long millis) {
            now += millis;
        }
    }

    private static Fake broker(boolean enabled, int minInterval, int maxPerSession, int maxSessions, int maxChannels) {
        Fake target = new Fake();
        setField(target, "enabled", enabled);
        setField(target, "minIntervalSeconds", minInterval);
        setField(target, "maxChannelsPerSession", maxPerSession);
        setField(target, "maxSessionsPerChannel", maxSessions);
        setField(target, "maxChannels", maxChannels);
        return target;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = SocketTickBroker.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("未启用时订阅被拒并给出原因，客户端据此退回轮询")
    void rejectsWhenDisabled() {
        SocketTickBroker.Subscription result = broker(false, 5, 8, 500, 200)
                .subscribe("datav:screen1", UUID.randomUUID(), 10);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).contains("未启用");
    }

    @Test
    @DisplayName("请求比下限更快的节拍会被抬到下限，并把实际值回给客户端")
    void clampsIntervalToFloor() {
        SocketTickBroker target = broker(true, 5, 8, 500, 200);

        SocketTickBroker.Subscription result = target.subscribe("datav:screen1", UUID.randomUUID(), 1);

        assertThat(result.accepted()).isTrue();
        assertThat(result.intervalMillis()).isEqualTo(5000);
    }

    @Test
    @DisplayName("到点才发、每次 seq 递增；没到点一次都不发")
    void emitsOnlyWhenDue() {
        Fake target = broker(true, 5, 8, 500, 200);
        target.subscribe("c1", UUID.randomUUID(), 5);

        target.advance(1000);
        assertThat(target.pollDue()).isEmpty();

        target.advance(4000);
        assertThat(target.pollDue()).hasSize(1);
        assertThat(target.pollDue()).as("下一拍之前不重复发").isEmpty();

        target.advance(5000);
        assertThat(target.pollDue().get(0).seq()).isEqualTo(2);
    }

    @Test
    @DisplayName("错过若干拍只补一次当前拍，不堆积历史节拍")
    void neverQueuesMissedTicks() {
        Fake target = broker(true, 5, 8, 500, 200);
        target.subscribe("c1", UUID.randomUUID(), 5);
        target.advance(60_000);

        assertThat(target.pollDue()).hasSize(1);
        // 下一次仍在正常间隔后，而不是把欠的 12 拍连环发出
        target.advance(4999);
        assertThat(target.pollDue()).isEmpty();
        target.advance(1);
        assertThat(target.pollDue()).hasSize(1);
    }

    @Test
    @DisplayName("同一频道多个订阅者取最快节拍：慢的人被顺带叫醒，不会更旧")
    void fastestIntervalWins() {
        Fake target = broker(true, 5, 8, 500, 200);
        target.subscribe("c1", UUID.randomUUID(), 30);
        SocketTickBroker.Subscription second = target.subscribe("c1", UUID.randomUUID(), 5);

        assertThat(second.intervalMillis()).isEqualTo(5000);
        assertThat(target.subscriberCount("c1")).isEqualTo(2);
        assertThat(target.channelCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("最后一个观众退订后频道立即回收，不被历史流量吃满上限")
    void reclaimsEmptyChannels() {
        Fake target = broker(true, 5, 8, 500, 200);
        UUID session = UUID.randomUUID();
        target.subscribe("c1", session, 5);
        assertThat(target.channelCount()).isEqualTo(1);

        target.unsubscribe("c1", session);

        assertThat(target.channelCount()).isZero();
        assertThat(target.pollDue()).isEmpty();
    }

    @Test
    @DisplayName("反复订阅同一频道不占多个名额，也不该被自己的上限拒掉")
    void resubscribeDoesNotConsumeExtraQuota() {
        Fake target = broker(true, 5, 2, 500, 200);
        UUID session = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            assertThat(target.subscribe("c1", session, 5).accepted()).isTrue();
        }
        assertThat(target.channelsOf(session)).containsExactly("c1");
        assertThat(target.subscriberCount("c1")).isEqualTo(1);
    }

    @Test
    @DisplayName("单连接订阅数超上限时拒绝，并说明上限")
    void enforcesPerSessionCap() {
        Fake target = broker(true, 5, 2, 500, 200);
        UUID session = UUID.randomUUID();
        target.subscribe("c1", session, 5);
        target.subscribe("c2", session, 5);

        SocketTickBroker.Subscription third = target.subscribe("c3", session, 5);

        assertThat(third.accepted()).isFalse();
        assertThat(third.reason()).contains("2");
    }

    @Test
    @DisplayName("频道总数达上限时拒新，并明确让客户端改用轮询")
    void enforcesChannelBudget() {
        Fake target = broker(true, 5, 8, 500, 2);
        target.subscribe("c1", UUID.randomUUID(), 5);
        target.subscribe("c2", UUID.randomUUID(), 5);

        SocketTickBroker.Subscription third = target.subscribe("c3", UUID.randomUUID(), 5);

        assertThat(third.accepted()).isFalse();
        assertThat(third.reason()).contains("轮询");
    }

    @Test
    @DisplayName("观众超上限时请走最早订阅的那批，新观众照样能看")
    void evictsOldestSubscribers() {
        Fake target = broker(true, 5, 8, 2, 200);
        UUID first = UUID.randomUUID();
        target.subscribe("c1", first, 5);
        target.subscribe("c1", UUID.randomUUID(), 5);
        target.subscribe("c1", UUID.randomUUID(), 5);

        assertThat(target.subscriberCount("c1")).isEqualTo(2);
        assertThat(target.channelsOf(first)).isEmpty();
    }

    @Test
    @DisplayName("断开清登记：连接名下所有频道都把它摘掉")
    void dropsAllChannelsOnDisconnect() {
        Fake target = broker(true, 5, 8, 500, 200);
        UUID session = UUID.randomUUID();
        target.subscribe("c1", session, 5);
        target.subscribe("c2", session, 5);

        target.dropSession(session);

        assertThat(target.channelCount()).isZero();
        assertThat(target.channelsOf(session)).isEmpty();
    }

    @Test
    @DisplayName("载荷只有频道名/时间戳/序号 —— 出现数据字段就是越权读取的口子")
    void payloadCarriesNoData() {
        Fake target = broker(true, 5, 8, 500, 200);
        target.subscribe("c1", UUID.randomUUID(), 5);
        target.advance(5000);

        assertThat(target.pollDue().get(0).payload()).containsOnlyKeys("channel", "at", "seq");
    }
}
