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

package com.privernal.job.context;

import com.privernal.common.datascope.aspect.DataScopeHelper;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.job.domain.SystemScheduleTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T10 用例 8d（落地注意4）：执行池线程的 TTL 残留。
 *
 * <p>断言两件事：① 调度执行进入的 ignore/组织上下文在 Scope.close()（finally 成对清理）后
 * 不残留给同一线程的下一个任务；② 反证——若不清理，TransmittableThreadLocal 在自建池
 * （无 TtlRunnable 包装）上确实会把上一个任务的 ignore 残留给下一个任务。两者合起来
 * 证明"finally clear() 生效"且"不清才会残留"。</p>
 */
@DisplayName("§13 用例 8d：执行池 TTL 残留（set/clear 成对）")
class ContextTtlResidueTest {

    private final ScheduleContextRestorer restorer = new ScheduleContextRestorer(null);

    private SystemScheduleTask task(String orgId) {
        SystemScheduleTask task = new SystemScheduleTask();
        task.setId("ttl-probe-task");
        task.setOrganizationId(orgId);
        return task;
    }

    @AfterEach
    void cleanup() {
        OrganizationContextHolder.clear();
    }

    @Test
    @DisplayName("enter() 装配组织+豁免，Scope.close() 后全部清空")
    void enterAndCloseLeavesNoResidue() {
        assertThat(OrganizationContextHolder.getOrganizationId()).isNull();
        try (ScheduleContextRestorer.Scope ignored = restorer.enter(task("org-A"))) {
            assertThat(OrganizationContextHolder.getOrganizationId()).isEqualTo("org-A");
            assertThat(Boolean.TRUE.equals(DataScopeHelper.getIgnoreDataPermission())).isTrue();
        }
        assertThat(OrganizationContextHolder.getOrganizationId()).as("组织残留").isNull();
        assertThat(Boolean.TRUE.equals(DataScopeHelper.getIgnoreDataPermission())).as("豁免残留").isFalse();
        assertThat(OrganizationContextHolder.isIgnore()).isFalse();
    }

    @Test
    @DisplayName("内核线程 enterKernelOnly 同样成对清理")
    void kernelScopeClears() {
        try (ScheduleContextRestorer.Scope ignored = restorer.enterKernelOnly()) {
            assertThat(OrganizationContextHolder.isIgnore()).isTrue();
            assertThat(Boolean.TRUE.equals(DataScopeHelper.getIgnoreDataPermission())).isTrue();
        }
        assertThat(OrganizationContextHolder.isIgnore()).isFalse();
        assertThat(Boolean.TRUE.equals(DataScopeHelper.getIgnoreDataPermission())).isFalse();
    }

    @Test
    @DisplayName("池线程复用：任务A 成对清理后，任务B 读到干净上下文（8d 主断言）")
    void pooledThreadReuseSeesCleanState() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "schedule-exec-probe");
            t.setDaemon(true);
            return t;
        });
        try {
            // 任务A：正常路径（try-with-resources 成对清理）
            pool.submit(() -> {
                try (ScheduleContextRestorer.Scope ignored = restorer.enter(task("org-A"))) {
                    assertThat(OrganizationContextHolder.getOrganizationId()).isEqualTo("org-A");
                }
            }).get(5, TimeUnit.SECONDS);
            // 任务B：同一池线程，断言 A 的 ignore 未残留
            AtomicReference<String> leaked = new AtomicReference<>("sentinel");
            AtomicReference<Boolean> leakedIgnore = new AtomicReference<>(Boolean.TRUE);
            pool.submit(() -> {
                leaked.set(OrganizationContextHolder.getOrganizationId());
                leakedIgnore.set(Boolean.TRUE.equals(DataScopeHelper.getIgnoreDataPermission()));
            }).get(5, TimeUnit.SECONDS);
            assertThat(leaked.get()).as("A 清理后 B 不应看到组织残留").isNull();
            assertThat(leakedIgnore.get()).as("A 清理后 B 不应看到豁免残留").isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("反证：只 set 不 clear 时残留确实发生（不清才会串——清理是必要条件）")
    void residueOccursWithoutClose() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "schedule-exec-probe-2");
            t.setDaemon(true);
            return t;
        });
        try {
            pool.submit(() -> {
                ScheduleContextRestorer.Scope scope = restorer.enter(task("org-LEAK"));
                // 故意不 close —— 模拟"设了不清"的错误用法
                assertThat(scope).isNotNull();
            }).get(5, TimeUnit.SECONDS);
            AtomicReference<String> residue = new AtomicReference<>();
            pool.submit(() -> residue.set(OrganizationContextHolder.getOrganizationId()))
                    .get(5, TimeUnit.SECONDS);
            assertThat(residue.get()).as("未清理时 TTL 残留必然出现").isEqualTo("org-LEAK");
        } finally {
            pool.shutdownNow();
        }
    }
}
