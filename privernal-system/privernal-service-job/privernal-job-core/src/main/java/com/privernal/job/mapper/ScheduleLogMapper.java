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

package com.privernal.job.mapper;

import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.job.domain.SystemScheduleInstance;
import com.privernal.job.domain.SystemScheduleTaskLog;
import com.privernal.job.filter.ScheduleLogFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 执行日志 / 实例心跳 Mapper。
 *
 * <p>日志表无 department_id 列，行级部门范围不适用；组织过滤由 SQL 显式按
 * organization_id 条件完成（服务层传当前组织，超管可查全量）。实例表是全局观测数据。</p>
 *
 * @author saperliu
 */
@IgnoreDataScope
public interface ScheduleLogMapper {

    List<SystemScheduleTaskLog> selectScheduleLogList(@Param("filter") ScheduleLogFilterDTO filter);

    SystemScheduleTaskLog selectScheduleLogById(@Param("id") Long id);

    /** 执行链回写日志终态（成功/失败/超时/取消） */
    int updateLogResult(@Param("id") Long id, @Param("status") String status,
                        @Param("endTime") String endTime, @Param("costMs") Integer costMs,
                        @Param("errorMsg") String errorMsg, @Param("resultDigest") String resultDigest);

    /** 同任务同 fire_time 的最大 attempt（重试链推导） */
    Integer selectMaxAttempt(@Param("taskId") String taskId, @Param("fireTime") String fireTime);

    /** 按幂等唯一键反查整行（INSERT IGNORE 后取自增 id） */
    SystemScheduleTaskLog selectLogByUniqueId(@Param("taskId") String taskId, @Param("fireTime") String fireTime,
                                              @Param("attempt") int attempt);

    int heartbeatInstance(SystemScheduleInstance instance);

    List<SystemScheduleInstance> selectInstanceList();

    /** 优雅停机时注销本实例（避免实例表留下 role 停留在 LEADER 的僵尸行，实例页最多展示到 6h 清理周期） */
    int deleteInstance(@Param("instanceId") String instanceId);

    /** 调度锁状态（实例页观测） */
    List<Map<String, Object>> selectLockState();

    int purgeStaleInstances(@Param("seconds") int seconds);

    /** 启动时补齐全部锁行（§6.5/§13 用例 15：行被清后抢主 UPDATE 影响 0 行=静默全瘫，启动 INSERT IGNORE 自动补齐） */
    int ensureLockRows();

    // ===== 任务报警列表聚合查询（§15，D5-C 一期落点，全部按 fire_time 分区键口径）=====

    /** 近 1 小时各状态日志计数：misfire 率/SKIPPED_POOL/TIMEOUT 等 */
    List<Map<String, Object>> selectLogStatusCountsLastHour();

    /** 近 1 小时按应用的成功/失败计数（聚合失败率 >10% 且失败>20 告警用） */
    List<Map<String, Object>> selectAppFailureStatsLastHour();

    // ===== 指标快照（D5-C 二期方案 B）=====

    /** 批量写快照 */
    int insertMetricSnapshots(@Param("samples") List<com.privernal.job.config.ScheduleMetrics.MetricSample> samples);

    /** 窗口内快照（Java 侧差分算增量/均值） */
    List<Map<String, Object>> selectMetricSnapshots(@Param("sinceMinutes") int sinceMinutes);

    /** 清理过期快照 */
    int purgeMetricSnapshots(@Param("retainDays") int retainDays);

    /** 日志分区维护（§6.2 按月分区滚动）：现有分区名列表，如 [p202610, p202611] */
    List<String> selectLogPartitionNames();

    /** 补建下一个月分区（name=p202611, upperBound='2026-12-01'） */
    int addLogPartition(@Param("name") String name, @Param("upperBound") String upperBound);

    /** 删过期分区（调用前须先 archiveOldLogs 搬走非 SUCCESS 行，§6.3） */
    int dropLogPartition(@Param("name") String name);

    /** SYSTEM 任务落 organization_id 用：取平台默认组织（建表最早的一个） */
    String selectDefaultOrganizationId();

    /** 该任务最新一条 RUNNING 日志（僵尸回收时置 TIMEOUT，§7.7） */
    SystemScheduleTaskLog selectLatestRunningLog(@Param("taskId") String taskId);

    /** call-log-purge 系统动作：清理 OpenAPI 调用流水（缺口的承接） */
    int purgeOpenapiCallLog(@Param("retainDays") int retainDays, @Param("limit") int limit);

    /**
     * outbox-compensate 系统动作：物理清理超期终态 webhook 事件（3 天）。
     * 必须走 SQL 而不是 MP wrapper-delete：全局逻辑删配置会把 DELETE 改写成
     * UPDATE deleted_flag='Y'，行数永远不减，留存就落空了。
     */
    int purgeWebhookEvents(@Param("retainDays") int retainDays, @Param("limit") int limit);
}
