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

package com.privernal.dynamic.dbs.groovy;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 每组织脚本并发配额（此前只有一个全局线程池，
 * 单组织可把池占满拖垮其它组织的脚本执行）。配额按组织上下文取，取不到按 _global_ 归桶。
 * <p>
 * 配置 {@code privernal.dynamic.script.org-concurrency}，默认 8；<=0 视为不限制。
 * 信号量按组织懒建，不复位——单组织偶发并发尖峰后信号量许可自动随 release 归还。
 */
@Component
public class ScriptConcurrencyGuard {

    private static final String GLOBAL_BUCKET = "_global_";

    @Value("${privernal.dynamic.script.org-concurrency:8}")
    private int orgConcurrency;

    private final Map<String, Semaphore> quotas = new ConcurrentHashMap<>();

    /** 占用一个组织级脚本并发名额，超限抛业务异常（调用方无需 finally 之外的补偿） */
    public <T> T runWithQuota(QuotaCallable<T> callable) throws Exception {
        Semaphore permit = null;
        if (orgConcurrency > 0) {
            String orgId = OrganizationContextHolder.getOrganizationId();
            String bucket = orgId == null || orgId.isEmpty() ? GLOBAL_BUCKET : orgId;
            permit = quotas.computeIfAbsent(bucket, k -> new Semaphore(orgConcurrency));
            if (!permit.tryAcquire()) {
                throw new ServiceException("脚本执行并发已达组织上限（" + orgConcurrency + "），请稍后重试");
            }
        }
        try {
            return callable.call();
        } finally {
            if (permit != null) {
                permit.release();
            }
        }
    }

    @FunctionalInterface
    public interface QuotaCallable<T> {
        T call() throws Exception;
    }
}
