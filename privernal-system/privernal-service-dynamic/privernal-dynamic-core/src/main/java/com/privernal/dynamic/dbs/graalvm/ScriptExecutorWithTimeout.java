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
import org.graalvm.polyglot.Context;

import java.util.concurrent.*;

/**
 * 控制线程的执行时间
 */
public class ScriptExecutorWithTimeout {

    public static Object executeWithTimeout(String script, long timeout, TimeUnit unit) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<Object> task = () -> {
            try (Context context = GraalVMContextPool.borrowContext()) {
                return context.eval("groovy", script).as(Object.class);
            }
        };
        Future<Object> future = executor.submit(task);
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException e) {
            future.cancel(true); // 取消任务
            throw new ServiceException("Script execution timed out");
        } finally {
            executor.shutdownNow(); // 清理资源
        }
    }
}
