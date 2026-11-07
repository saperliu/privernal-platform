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

package com.privernal.job.support;

import cn.hutool.json.JSONObject;
import com.privernal.job.api.ScheduleTaskClient;
import com.privernal.job.api.TaskRef;
import com.privernal.job.context.ScheduleContextRestorer;
import com.privernal.job.domain.SystemScheduleTask;
import com.privernal.job.mapper.ScheduleLogMapper;
import com.privernal.job.service.ScheduleTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * SYSTEM 存量任务注册器（P0：内置任务迁入）。
 *
 * <p>启动时幂等注册（registerCron 命中 uk_biz 即改期，不重复建行）：
 * call-log-purge —— OpenAPI 调用流水每日清理（缺口承接）；
 * column-snapshot-refresh / date-field-recalc —— 列快照与日期字段重算（已随 §8.3 前缀口径落地迁入）。
 * SaIdTokenRefreshTask 迁移涉及登录链路风险，本期保留原 @Scheduled 不动。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemTaskRegistrar implements ApplicationRunner {

    private final ScheduleTaskClient scheduleTaskClient;

    private final ScheduleLogMapper logMapper;

    private final ScheduleContextRestorer contextRestorer;

    @Override
    public void run(ApplicationArguments args) {
        try (ScheduleContextRestorer.Scope scope = contextRestorer.enterKernelOnly()) {
            // 锁行自动补齐（§6.5/§13 用例 15）：行缺失时抢主 UPDATE 影响 0 行=所有实例静默"不是主"，
            // 全程无报错；启动 INSERT IGNORE 兜底，幂等可重入
            int ensured = logMapper.ensureLockRows();
            if (ensured > 0) {
                log.warn("[schedule] 补齐了 {} 条缺失的锁行（此前调度会静默全瘫）", ensured);
            }
            String orgId = logMapper.selectDefaultOrganizationId();
            if (orgId == null) {
                log.warn("[schedule] no organization found, skip SYSTEM task registration");
                return;
            }
            registerCallLogPurge(orgId);
        } catch (Exception e) {
            log.error("[schedule] SYSTEM task registration failed", e);
        }
    }

    private void registerCallLogPurge(String orgId) {
        try {
            TaskRef ref = scheduleTaskClient.registerCron(
                    orgId, "SYSTEM", "sys:call-log-purge", "call-log-purge",
                    "0 40 3 * * ?", "BEAN",
                    cn.hutool.json.JSONUtil.createObj().set("bean", "call-log-purge"), null);
            log.info("[schedule] SYSTEM call-log-purge registered: {} (upsert={})", ref.getTaskId(), ref.isUpserted());
        } catch (Exception e) {
            log.error("[schedule] register call-log-purge failed", e);
        }
 // 承接 ：Webhook outbox 补偿（每 1 分钟扫超 2 分钟仍 PENDING 的事件重投）
        registerFixedDelay(orgId, "sys:outbox-compensate", "outbox-compensate", 60,
                cn.hutool.json.JSONUtil.createObj().set("bean", "outbox-compensate"));
 // 承接 ：数据集列快照每日校验刷新（前缀阻断已随 重载解除）
        registerCronJob(orgId, "sys:column-snapshot-refresh", "column-snapshot-refresh",
                "0 10 4 * * ?", "column-snapshot-refresh");
        // §17 SYSTEM 存量迁入：sa-token same-token 主动刷新（原 SaIdTokenRefreshTask 的 @Scheduled 已移除）
        registerFixedDelay(orgId, "sys:same-token-refresh", "same-token-refresh", 300,
                cn.hutool.json.JSONUtil.createObj().set("bean", "same-token-refresh"));
        // D5-C 二期方案 B：调度指标快照（每分钟，报警列表 30 分钟窗口趋势数据源）
        registerFixedDelay(orgId, "sys:metrics-snapshot", "metrics-snapshot", 60,
                cn.hutool.json.JSONUtil.createObj().set("bean", "metrics-snapshot"));
        // P2-B：DATE_FIELD 每日全量差量重算（写入钩子遗漏的兜底；凌晨低峰执行）
        registerCronJob(orgId, "sys:date-field-recalc", "date-field-recalc",
                "0 30 1 * * ?", "date-field-recalc");
    }

    private void registerFixedDelay(String orgId, String bizKey, String code, int intervalSeconds, Object config) {
        try {
            // 走 client 幂等 upsert（save 是管理面新建，撞 uk_biz 会报 DuplicateKey）
            TaskRef ref = scheduleTaskClient.registerFixedDelay(orgId, "SYSTEM", bizKey, code,
                    intervalSeconds, "BEAN", config, null);
            log.info("[schedule] SYSTEM {} registered (interval={}s, upsert={})", code, intervalSeconds, ref.isUpserted());
        } catch (Exception e) {
            log.error("[schedule] register {} failed", code, e);
        }
    }

    private void registerCronJob(String orgId, String bizKey, String code, String cron, String bean) {
        try {
            scheduleTaskClient.registerCron(orgId, "SYSTEM", bizKey, code, cron, "BEAN",
                    cn.hutool.json.JSONUtil.createObj().set("bean", bean), null);
            log.info("[schedule] SYSTEM {} registered (cron={})", code, cron);
        } catch (Exception e) {
            log.error("[schedule] register {} failed", code, e);
        }
    }
}
