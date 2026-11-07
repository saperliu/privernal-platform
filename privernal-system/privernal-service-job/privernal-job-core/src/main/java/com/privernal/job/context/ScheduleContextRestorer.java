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

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.dynamic.dbs.service.IDynamicDatabaseService;
import com.privernal.dynamic.dto.DynamicDatabaseDTO;
import com.privernal.job.domain.SystemScheduleTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 调度/执行线程上下文恢复（——"这条不做整个模块不可用"）。
 *
 * <p>调度与执行线程没有登录态（SecurityUtils 三方法在非 Web 线程均返回空串），
 * 每次执行必须装配四件事，并在 finally 成对清理（TTL 在自建池上不会自动清理，
 * 上一个任务的 ignore 残留会放行下一个不该放行的 SQL，§9.3 落地注意4）：</p>
 *
 * <ol>
 *   <li>组织上下文：setOrgAndIgnoreDepartment(orgId, true)（WebhookDispatcher 同款先例）；</li>
 *   <li>数据权限豁免：同一次调用完成（组织 + 行级部门一起放行，防 1 = 2 改写）；</li>
 *   <li>业务库名：优先按 organization_id 现场解析（selectDynamicDatabase，组织改绑库后不写旧库），
 *       解析不到回落任务行的 database_name 快照，两者都缺则拒绝执行记 FAILED；</li>
 *   <li>物理表名前缀：由 DynamicTableCodePrefixService.decorate(tableCode, orgId) 重载承担
 *       （currentPrefix 先读 OrganizationContextHolder 再回落会话——本类第 1 项已把组织放进 TTL）。</li>
 * </ol>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleContextRestorer {

    private static final String DEFAULT_DATABASE = "master";

    private final IDynamicDatabaseService dynamicDatabaseService;

    /**
     * 进入任务执行上下文。用法：
     * <pre>try (ScheduleContextRestorer.Scope ignored = restorer.enter(task)) { ... }</pre>
     */
    public Scope enter(SystemScheduleTask task) {
        OrganizationContextHolder.setOrgAndIgnoreDepartment(task.getOrganizationId(), true);
        return new Scope();
    }

    /** 无具体任务行的场景（扫描/对账等内核批处理）：只做线程级豁免 */
    public Scope enterKernelOnly() {
        OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
        return new Scope();
    }

    /**
     * 业务库名解析（§8.3 第3项）：现场解析优先，快照兜底，两者皆空返回 null（调用方记 FAILED）。
     * 必须在 enter() 之后的豁免上下文内调用。
     */
    public String resolveDatabase(SystemScheduleTask task) {
        try {
            DynamicDatabaseDTO dto = dynamicDatabaseService.selectDynamicDatabase(task.getOrganizationId());
            if (dto != null && StringUtils.isNotEmpty(dto.getDatabaseName())) {
                return dto.getDatabaseName();
            }
        } catch (Exception e) {
            log.warn("[schedule] 按 org 解析业务库失败，回落任务快照 orgId={} taskId={}",
                    task.getOrganizationId(), task.getId(), e);
        }
        return StringUtils.isNotEmpty(task.getDatabaseName()) ? task.getDatabaseName() : null;
    }

    /** 默认库名（无业务数据动作的任务使用） */
    public String defaultDatabase() {
        return DEFAULT_DATABASE;
    }

    /** 成对清理的闭环句柄 */
    public static class Scope implements AutoCloseable {

        @Override
        public void close() {
            OrganizationContextHolder.clear();
        }
    }
}
