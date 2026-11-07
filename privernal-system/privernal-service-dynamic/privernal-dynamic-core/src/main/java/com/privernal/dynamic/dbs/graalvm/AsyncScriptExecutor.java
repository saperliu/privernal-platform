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

package com.privernal.dynamic.dbs.graalvm;

import com.privernal.common.core.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.privernal.dynamic.dbs.graalvm.ScriptExecutorWithTimeout.executeWithTimeout;

/**
 * 异步执行脚本
 * @author saperliu
 * 执行脚本控制线程数据
 */
@Slf4j
public class AsyncScriptExecutor {

    private static final int MAX_CONCURRENT_SCRIPTS = 100;
    private static final int TIMEOUT = 5;
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_SCRIPTS);

    public static void executeScriptAsync(String script, CompletableFuture<Object> future) {

        executor.submit(() -> {
            try {
//                Context  context = GraalVMContextPool.borrowContext();
//                Value result = context.eval("groovy", script);
                Object result = executeWithTimeout(script, TIMEOUT, TimeUnit.SECONDS);
                future.complete(result);
            } catch (Exception e) {
                log.error("executeScriptAsync", e);
                future.completeExceptionally(e);
            }
        });
    }

    public static Object executeScript(Context context, String script) {
        try {
            Callable<Object> task = () -> context.eval("groovy", script).as(Object.class);
            Future<Object> future = executor.submit(task);
            try {
                return future.get(TIMEOUT, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true); // 取消任务
                throw new ServiceException("Script execution timed out");
            }
            // 共享线程池不允许在 finally 中 shutdownNow，否则首次超时后全局脚本执行都会被拒绝
        } catch (Exception e) {
            log.error("Script execution failed: ", e);
        }
        return null;
    }
}
