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

package com.privernal.dynamic.advanced.engine;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 联动专用执行池（§7.5.5）：core 4 / max 16 / 有界队列 2000 + CallerRuns，
 * 不复用 Boot 默认调度池（size=1，且已被既有 2 个 @Scheduled 占用）。
 * <p>
 * CallerRuns 兜底：队列满时由提交线程（事件 afterCommit 线程）自己跑，事件不丢但会拖慢提交线程，
 * 同时打告警日志提示扩容。
 */
@Slf4j
@Component
public class LinkageExecutorPool {

    private static final int CORE = 4;
    private static final int MAX = 16;
    private static final int QUEUE_CAPACITY = 2000;

    private final ThreadPoolExecutor pool;
    private final AtomicInteger running = new AtomicInteger();

    public LinkageExecutorPool() {
        this.pool = new ThreadPoolExecutor(CORE, MAX, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread thread = new Thread(r, "linkage-worker-" + running.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        this.pool.allowCoreThreadTimeOut(true);
    }

    public void submit(Runnable task) {
        int queued = pool.getQueue().size();
        if (queued > QUEUE_CAPACITY * 3 / 4) {
            log.warn("联动执行池队列积压 {}/{}，接近 CallerRuns 兜底线，建议扩容", queued, QUEUE_CAPACITY);
        }
        pool.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                // 执行器内已兜底记录，这里是最后一道防线：绝不让异常杀死池线程
                log.error("联动任务未捕获异常", e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }
}
