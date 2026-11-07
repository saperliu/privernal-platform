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

package com.privernal.dynamic.datav.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.security.service.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 数据集级熔断器（M6.6）。
 * <p>
 * 投放屏每 30s 轮询一次 {@code /back/datav/data/batch}，50 块屏 × 每屏约 10 个数据集组件时，
 * 一个坏掉的数据集会被「每块屏的每个组件、每轮刷新」反复打库——它自己那条 SQL 占着请求线程直到超时，
 * 还会把连接池拖干，最后表现为整面墙一起卡死。这里给「连续失败」装一道闸：达到阈值就短路一段时间，
 * 快速失败不再打库，让源站有喘息、也让请求线程能被释放去服务别的屏。
 * <p>
 * 状态键刻意带上组织维度（{@link #identityScope()}），与 {@link DatavResultCache} 的口径一致：
 * A 组织自己的坏数据集不能把熔断打开到 B 组织头上——否则一个租户的故障会放大成全网故障。
 * <p>
 * 半开只放一个探测：冷却到点后如果放一批并发过去探测，等于在最脆弱的一刻把坏数据集重新
 * 打成雪崩；放一个试探，成功即闭合、失败即重新计时。
 * <p>
 * 默认关闭（{@code breaker-enabled=false}），与本项目所有 datav 开关同一交付姿态：新能力必须
 * 由运维显式开启，不在默认路径上悄悄改变线上行为。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class DatavQueryBreaker {

    /** 总开关：默认 false。开启等于允许平台在数据集连续失败时主动拒绝取数 */
    @Value("${privernal.datav.breaker-enabled:false}")
    boolean enabled;

    /**
     * 连续失败多少次后熔断。默认 3。
     * <p>
     * 取 3 是为了「容忍一次偶发抖动、又不放任真故障」：投放屏 30s 一轮，3 次就是约 90s 持续失败
     * 才跳闸；设成 1 会把一次网络毛刺误判成故障，设成 10 则跳闸前已经白发几十次力。
     */
    @Value("${privernal.datav.breaker-failure-threshold:3}")
    int failureThreshold;

    /**
     * 熔断冷却时长（毫秒），默认 30000。
     * <p>
     * 取 30s 是与前端刷新周期对齐：跳闸后到下一次整屏刷新正好落在冷却末端，进入半开发一次探测，
     * 恢复即闭合、失败即再冷却一轮，运维看到的是「坏数据集每 30s 试一次」而不是「每 30s 打爆一次」。
     */
    @Value("${privernal.datav.breaker-cooldown-millis:30000}")
    long cooldownMillis;

    /** 组织 + 数据集编码 → 熔断状态；进程内即可，跨实例各熔断各的没有正确性问题 */
    private final Map<String, State> states = new ConcurrentHashMap<>();

    /**
     * 时钟缝隙：单独抽出以便用例注入假时钟（{@link System#currentTimeMillis()} 不可控，
     * 冷却窗口无法用短等待可靠验证）。
     */
    long nowMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 身份维度：只取组织 id。
     * <p>
     * 与 {@link DatavResultCache#identityScope()} 不同，这里<b>不带</b>部门/用户——缓存键少一项
     * 维度是数据泄露，而熔断键少一项只是「同一数据集在同组织内共享一条坏消息」，那正是我们要的
     * 收敛效果；带部门/用户反而会让坏数据集在部门间各熔断各的，闸门形同虚设。
     * 无登录上下文时 SecurityUtils 直接抛，不在这里兜底占位。
     */
    String identityScope() {
        return SecurityUtils.getOrganizationId();
    }

    public <T> T guard(String datasetCode, Supplier<T> query) {
        return guard(datasetCode, identityScope(), query);
    }

    /**
     * 包私有重载：作用域由调用方给出，用例据此在无 Spring、无登录态的环境下直接验证跨组织隔离。
     */
    <T> T guard(String datasetCode, String orgScope, Supplier<T> query) {
        // 关闭、阈值非正或冷却非正都视为不熔断：保留「直接执行」的旧行为，不引入任何状态
        if (!enabled || failureThreshold <= 0 || cooldownMillis <= 0) {
            return query.get();
        }
        State state = states.computeIfAbsent(orgScope + "|" + datasetCode, k -> new State());
        boolean probe;
        synchronized (state) {
            if (!admit(state)) {
                throw openFailure(datasetCode, state);
            }
            probe = state.probeInFlight;
        }
        try {
            T result = query.get();
            recordSuccess(state, probe);
            return result;
        } catch (RuntimeException | Error e) {
            recordFailure(state, probe);
            throw e;
        }
    }

    /**
     * 判定本次调用能否放行，并在放行时把状态推进到「正在执行」。必须在持有 state 锁时调用。
     *
     * @return true 表示放行；此时 {@code state.probeInFlight} 标记本次是否为半开探测
     */
    private boolean admit(State state) {
        long now = nowMillis();
        if (state.opened) {
            if (now - state.openedAtMillis < cooldownMillis) {
                return false;                       // 仍在冷却窗口内，快速失败
            }
            // 冷却到点：进入半开，但同一时刻只允许一个探测在飞
            if (state.probeInFlight) {
                return false;
            }
            state.probeInFlight = true;
            return true;
        }
        state.probeInFlight = false;
        return true;
    }

    private void recordSuccess(State state, boolean probe) {
        synchronized (state) {
            if (probe) {
                state.probeInFlight = false;
            }
            state.opened = false;
            state.consecutiveFailures = 0;
        }
    }

    private void recordFailure(State state, boolean probe) {
        synchronized (state) {
            if (probe) {
                // 半开探测失败：立刻重新跳闸并重新计时，不必再攒够阈值（探测本身就是「再信你一次」）
                state.probeInFlight = false;
                state.opened = true;
                state.openedAtMillis = nowMillis();
                state.consecutiveFailures = failureThreshold;
                return;
            }
            state.consecutiveFailures++;
            if (state.consecutiveFailures >= failureThreshold) {
                state.opened = true;
                state.openedAtMillis = nowMillis();
                log.warn("大屏数据集熔断已打开：failures={} cooldownMs={}",
                        state.consecutiveFailures, cooldownMillis);
            }
        }
    }

    private ServiceException openFailure(String datasetCode, State state) {
        long remainMillis = Math.max(0L, cooldownMillis - (nowMillis() - state.openedAtMillis));
        return new ServiceException("数据集 " + datasetCode + " 暂时熔断（连续失败 "
                + state.consecutiveFailures + " 次），将在约 " + (remainMillis / 1000L)
                + " 秒后自动重试；这是保护性短路而非数据错误，无需排查该数据集本身");
    }

    /** 单个「组织+数据集」的熔断状态；字段一律在其自身监视器下读写 */
    private static final class State {
        boolean opened;
        long openedAtMillis;
        int consecutiveFailures;
        boolean probeInFlight;
    }
}
