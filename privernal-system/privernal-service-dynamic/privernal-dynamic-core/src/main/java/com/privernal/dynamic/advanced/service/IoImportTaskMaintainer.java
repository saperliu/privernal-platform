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

package com.privernal.dynamic.advanced.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.dynamic.advanced.domain.DynamicIoImportTask;
import com.privernal.dynamic.advanced.mapper.DynamicIoImportTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 导入任务守护（R2-19 导入导出增强）：
 * <ul>
 *   <li>断点续跑：每分钟扫描心跳过期的 RUNNING 任务（执行实例死亡/重启即心跳停更），
 *       CAS 认领（WHERE 带状态与心跳条件，多实例谁更到谁执行）后交本实例 executor 从 processed 断点续跑；</li>
 *   <li>保留期清理：每日删除超过 retention-days 的已结束任务（任务痕迹有保留期，避免 rows_json 无限膨胀）。</li>
 * </ul>
 * 配置键走 @Value 默认值（仿 DatavQueryLimiter/DocPrintPdfService，yaml 不必显式配置）。
 */
@Slf4j
@Component
public class IoImportTaskMaintainer {

    private final DynamicIoImportTaskMapper ioImportTaskMapper;
    private final IoDataService ioDataService;

    /** 心跳过期阈值（秒）：低于导入执行的最大安静窗口（进度 2s/次落心跳，90s 足够宽容） */
    @Value("${dynamic.io.import.stale-seconds:90}")
    private int staleSeconds;

    /** 已结束任务保留天数 */
    @Value("${dynamic.io.import.retention-days:30}")
    private int retentionDays;

    public IoImportTaskMaintainer(@Autowired DynamicIoImportTaskMapper ioImportTaskMapper,
                                  @Autowired IoDataService ioDataService) {
        this.ioImportTaskMapper = ioImportTaskMapper;
        this.ioDataService = ioDataService;
    }

    /** 心跳过期任务认领 + 续跑（单实例=重启自愈；多实例=任意存活实例接手） */
    @Scheduled(initialDelay = 15_000, fixedDelay = 60_000)
    public void claimStaleTasks() {
        Date staleBefore = new Date(System.currentTimeMillis() - staleSeconds * 1000L);
        List<DynamicIoImportTask> stale;
        try {
            // 调度线程无登录态：组织/部门过滤与数据范围切面按导入线程同口径忽略（否则
            // 组织拦截器在无上下文时要么报错要么把扫描过滤成 0 行，§5.5 同口径）
            OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
            try {
                stale = ioImportTaskMapper.selectList(Wrappers.<DynamicIoImportTask>lambdaQuery()
                        .eq(DynamicIoImportTask::getStatus, DynamicIoImportTask.STATUS_RUNNING)
                        .and(w -> w.isNull(DynamicIoImportTask::getHeartbeatTime)
                                .or().lt(DynamicIoImportTask::getHeartbeatTime, staleBefore))
                        .last("limit 50"));
            } finally {
                OrganizationContextHolder.clear();
            }
        } catch (Exception e) {
            log.warn("导入任务续跑扫描失败（下次周期重试）", e);
            return;
        }
        for (DynamicIoImportTask task : stale) {
            try {
                // CAS 认领：多实例并发扫描时只有一个 UPDATE 生效（rows=1 即本实例成为执行者）
                OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
                int claimed;
                try {
                    claimed = ioImportTaskMapper.claimStaleTask(task.getId(),
                            ioDataService.workerId(), staleBefore, new Date());
                } finally {
                    OrganizationContextHolder.clear();
                }
                if (claimed == 1) {
                    log.warn("导入任务心跳过期，认领续跑 taskId={} form={} processed={}/{}",
                            task.getId(), task.getFormId(), task.getProcessed(), task.getTotal());
                    ioDataService.resumeTask(task.getId());
                }
            } catch (Exception e) {
                log.warn("导入任务认领失败 taskId={}", task.getId(), e);
            }
        }
    }

    /** 保留期清理：只删已结束任务（在途任务永不清理） */
    @Scheduled(cron = "0 40 1 * * ?")
    public void cleanupExpiredTasks() {
        if (retentionDays <= 0) {
            return;
        }
        Date before = new Date(System.currentTimeMillis() - retentionDays * 24L * 3600 * 1000L);
        try {
            OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
            int deleted;
            try {
                deleted = ioImportTaskMapper.delete(Wrappers.<DynamicIoImportTask>lambdaQuery()
                        .in(DynamicIoImportTask::getStatus, DynamicIoImportTask.STATUS_FINISHED,
                                DynamicIoImportTask.STATUS_FAILED)
                        .lt(DynamicIoImportTask::getCreatedTime, before));
            } finally {
                OrganizationContextHolder.clear();
            }
            if (deleted > 0) {
                log.info("导入任务保留期清理 {} 条（retention-days={}）", deleted, retentionDays);
            }
        } catch (Exception e) {
            log.warn("导入任务保留期清理失败", e);
        }
    }
}
