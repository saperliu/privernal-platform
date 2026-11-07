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

package com.privernal.job.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置（合并项）。
 *
 * <p>现状：@EnableAsync 已开但全仓无 TaskExecutor bean，3 处 @Async 跑在 Boot 自动
 * applicationTaskExecutor（8 线程 + 无界队列）。本模块定义显式 Executor bean 后
 * Boot 自动配置 back off，@Async 统一落到有界队列 + CallerRuns 的池上。</p>
 *
 * <p>调度内核自己的 4 线程池在 ScheduleKernel 内自建（schedule-kernel 前缀），
 * 执行池在 ScheduleExecutorPool（schedule-exec 前缀），互不共用。</p>
 *
 * @author saperliu
 */
@Configuration
public class ScheduleAsyncConfig {

    @Bean("asyncExecutor")
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-task-");
        // 有界队列打满时由调用线程执行，背压而不是无界堆积（§12.3：现状无界队列是隐患）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
