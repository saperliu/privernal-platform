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
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 大屏取数并发上限（M6.6）。
 * <p>
 * {@code /back/datav/data/batch} 内部刻意串行（行级数据权限靠当前线程登录上下文），但<b>跨请求</b>
 * 并不设限：50 块屏轮询 + 轮播时，一批慢数据集能同时占住几十个 Servlet 线程直到各自的 SQL 超时，
 * 把 Tomcat 线程池与数据库连接一起抽干——单个坏查询会演变成整面墙打不开。这里给「真正打库的那一段」
 * 套一个公平信号量：超过上限的请求最多排队一小会儿，排不到就快速返回「下一轮刷新再试」，
 * 把「雪崩式排队」换成「可自愈的错峰重试」。
 * <p>
 * 许可<b>必须</b>在 finally 里归还：泄漏一个许可就等于少一份并发，攒够几次就把闸门永久焊死，
 * 那比没有限流更糟——所以这是本类唯一不容妥协的不变式（有专门用例覆盖异常路径不泄漏）。
 * <p>
 * 默认关闭（{@code max-concurrent-queries=0} 即不限并发），与本项目所有 datav 开关同一交付姿态：
 * 限流本身可能在一套健康系统上制造「排队超时」这种新错误，不该在默认路径上悄悄开启。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class DatavQueryLimiter {

    /**
     * 同时在跑的大屏取数上限。0（默认）= 不限，保持今日行为。
     * <p>
     * 建议生产值 8：单实例、连接池 10 的常见配置下，留 2 条连接给设计态预览与报表，
     * 8 条给投放屏轮询——轮询是持续负载，占满连接池会让编辑操作也一起卡住。
     */
    @Value("${privernal.datav.max-concurrent-queries:0}")
    int maxConcurrent;

    /**
     * 排队等许可的上限（毫秒），默认 2000。
     * <p>
     * 取 2s 是「宁可等一下也别白等」的折中：投放屏 30s 才刷一次，为一次取数多等 2s 观众无感；
     * 但再长就会让请求线程长时间挂住、反而加剧线程池压力，所以到点直接快速失败下一轮再来。
     */
    @Value("${privernal.datav.query-acquire-timeout-millis:2000}")
    long acquireTimeoutMillis;

    /** 公平信号量：先到先得，避免某块屏在尾部被长期饿死 */
    Semaphore semaphore;

    @PostConstruct
    void init() {
        if (maxConcurrent > 0) {
            this.semaphore = new Semaphore(maxConcurrent, true);
        }
    }

    public boolean enabled() {
        return maxConcurrent > 0 && semaphore != null;
    }

    /**
     * 在并发闸门内执行一次取数。
     * <p>
     * 关闭时直接执行、完全不碰信号量（不产生任何排队开销，也不依赖 init 是否被调用）。
     *
     * @param query 真正打库的那段
     * @return 查询结果
     * @throws ServiceException 排队超时——消息明确说「下一轮刷新再试」，不让它看起来像数据错误
     */
    public <T> T call(Supplier<T> query) {
        if (!enabled()) {
            return query.get();
        }
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // 恢复中断位并快速失败：这里不能吞中断继续等，否则关不掉正在排队的线程
            Thread.currentThread().interrupt();
            throw new ServiceException("大屏取数排队被中断，请在下一次刷新时重试");
        }
        if (!acquired) {
            throw new ServiceException("大屏取数并发已满，本次未执行查询；这是负载保护而非数据错误，"
                    + "将在下一次自动刷新（约 30 秒后）重试，无需排查数据集本身");
        }
        try {
            return query.get();
        } finally {
            // 唯一不容妥协的不变式：无论查询成功、抛异常还是 Error，许可都必须归还
            semaphore.release();
        }
    }
}
