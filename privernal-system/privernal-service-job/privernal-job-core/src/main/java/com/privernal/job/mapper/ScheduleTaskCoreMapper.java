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
import com.privernal.job.domain.SystemScheduleTask;
import com.privernal.job.domain.SystemScheduleTaskLog;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 调度内核 Mapper。
 *
 * <p>数据权限豁免"三件套"缺一不可：
 * ① 线程级豁免——内核线程进入时 OrganizationContextHolder.setIgnoreOrgAndDepartment(true)、finally clear；
 * ② 类上 @IgnoreDataScope 双保险（PrivernalDataScopeAspect 对 com.privernal. 前缀且无注解的 mapper 会把 WHERE
 *    整段替换为 1 = 2，UPDATE/DELETE 同样被改写且不报错——那是"任务永不触发"级静默失效）；
 * ③ 5 张 system_schedule_* 表已加入 OrganizationHandlerImpl.IGNORE_TABLES（挡无会话 INSERT 时
 *    organization_id 被写成 NULL）。</p>
 *
 * <p>本接口的全部语句都是显式 SQL（见 mapper/schedule/ScheduleTaskCoreMapper.xml），
 * 不使用 MP 条件构造器，保证扫描/CAS/回写的 WHERE 完全可控。</p>
 *
 * @author saperliu
 */
@IgnoreDataScope
public interface ScheduleTaskCoreMapper {

    // ===== 选主与租约（§7.3 / §6.5）=====

    /** 抢主：holder 为本实例或租约已过期时续期 30s；affected=1 即本轮调度主 */
    int tryAcquireSchedulerLock(@Param("instanceId") String instanceId);

    /** 持锁续租（每 10s 心跳；仅 holder 是本实例时生效） */
    int renewSchedulerLock(@Param("instanceId") String instanceId);

    // ===== 扫描与抢占（§7.1 / §7.2）=====

    /** 已到期（next_fire_time &lt;= NOW(3)）的 READY 行：ORDER BY next_fire_time ASC LIMIT 2000
     *  （P2-1：严格只取已到期行，不预取未来 2s——扫描每秒一轮，触发延迟上界 1s，且不会提前触发） */
    List<SystemScheduleTask> scanReady(@Param("limit") int limit);

    /** 已到期未触发的 READY 行数（§15 静默全瘫预警：扫描恒 0 行但此数 > 0 即数据权限/锁行失效） */
    long countReadyDue();

    /** 到期超 60s 仍未触发的 READY 行数（报警列表：持续 >0 即调度停滞） */
    long countReadyStale();

    /** 当前悬挂 TRIGGERED 行数（租约已过期，对账未回收） */
    long countHangingTriggers();

    /** 窗口内到期 READY 行数（§12.1 排队延迟估算：聚集数/2000 上限） */
    long countReadyInWindow(@Param("start") Date start, @Param("end") Date end);

    /** 命中 uk_biz 的未软删行（register 幂等 upsert 查行，§12.2） */
    SystemScheduleTask selectActiveByBizKey(@Param("orgId") String orgId, @Param("source") String source,
                                            @Param("bizKey") String bizKey);

    /** P1-2：应用包导入判重（app_id + task_code，与 uk_code 同口径；biz_key 跨环境不稳定） */
    SystemScheduleTask selectActiveByTaskCode(@Param("appId") String appId, @Param("taskCode") String taskCode);

    /** 级联取消候选（§7.8） */
    List<SystemScheduleTask> selectActiveByBizPrefix(@Param("orgId") String orgId, @Param("source") String source,
                                                     @Param("bizPrefix") String bizPrefix);

    SystemScheduleTask selectById(@Param("id") String id);

    /**
     * CAS 抢占：status=TRIGGERED + owner + 60s 租约；不改 version（注2）。
     * pendingNextFireTime 非空时同时预计算本轮执行完的提升值（周期任务防执行期宕机丢周期，§7.2）。
     */
    int casTrigger(@Param("id") String id, @Param("version") long version,
                   @Param("instanceId") String instanceId, @Param("pendingNextFireTime") Date pendingNextFireTime);

    // ===== 执行链（§7.2 顺序：CAS → 投池 → INSERT 日志 → RUNNING → 动作 → 回写）=====

    /** 幂等抢占执行权：INSERT IGNORE，affected=0 说明本轮已被执行/正在执行（双主/重复投递），放弃 */
    int insertLogIgnore(SystemScheduleTaskLog log);

    /** TRIGGERED → RUNNING（WHERE 带 owner 防止对账回收后被他人抢占） */
    int markRunning(@Param("id") String id, @Param("instanceId") String instanceId);

    /** 一次性任务成功终态：DONE + 清执行占用 + 重置轮次 */
    int finishOnce(@Param("id") String id, @Param("instanceId") String instanceId,
                   @Param("fireTime") Date fireTime);

    /** 周期任务成功：提升 pending 为 next、预计算下一轮 pending；回写带 running_instance 条件（§7.5） */
    int finishCycle(@Param("id") String id, @Param("instanceId") String instanceId,
                    @Param("fireTime") Date fireTime, @Param("nextFireTime") Date nextFireTime,
                    @Param("pendingNextFireTime") Date pendingNextFireTime);

    /** 失败回写：ONCE 按 max_retry 退避重试（attempt+1、next=now+backoff）或耗尽转 DONE；周期按 period 分档（§7.7）。countFail=false 用于 misfire 补跑失败（P1-6：不累加 fail_count） */
    int markFailed(@Param("id") String id, @Param("instanceId") String instanceId,
                   @Param("nextFireTime") Date nextFireTime, @Param("pendingNextFireTime") Date pendingNextFireTime,
                   @Param("nextAttempt") int nextAttempt, @Param("finalDone") boolean finalDone,
                   @Param("countFail") boolean countFail,
                   @Param("errorMsg") String errorMsg);

    /** 池满回退（§8.2）：ONCE 顺延 30s 重投本轮；周期直接回 READY 且 next=pending（不加 30s，相位不漂） */
    int revertPoolRejected(@Param("id") String id, @Param("once") boolean once);

    /** misfire 处置（§7.7）：跳过本轮（ONCE 转 DONE / 周期算下次），记 attempt=0 的 SKIPPED_MISFIRE 由服务层写日志 */
    int applyMisfire(@Param("id") String id, @Param("nextFireTime") Date nextFireTime,
                     @Param("pendingNextFireTime") Date pendingNextFireTime, @Param("once") boolean once);

    /** 连续失败达阈值自动暂停（§7.7，条件写在 SQL：fail_count >= threshold） */
    int autoPauseIfFailed(@Param("id") String id, @Param("threshold") int threshold);

    // ===== 对账（§7.5/§7.7：只处理 TRIGGERED 租约过期与 RUNNING 超 2×timeout 两类，RUNNING 未超时只读不改）=====

    List<SystemScheduleTask> selectExpiredTriggers(@Param("limit") int limit);

    List<SystemScheduleTask> selectZombieRunning(@Param("limit") int limit);

    /** 悬挂 TRIGGERED 回收：回 READY、清 owner/lease；next_fire_time 不动（已过期会被扫描/misfire 接管） */
    int recoverExpiredTrigger(@Param("id") String id, @Param("instanceId") String instanceId);

    /** 僵尸 RUNNING 回收：按传入的下一时间回 READY（或 ONCE 直接 DONE） */
    int recoverZombieRunning(@Param("id") String id, @Param("instanceId") String instanceId,
                             @Param("nextFireTime") Date nextFireTime,
                             @Param("pendingNextFireTime") Date pendingNextFireTime, @Param("once") boolean once);

    // ===== 手动执行与终止（§11/§12.2）=====

    /** 手动执行互斥：仅 READY/PAUSED 且无执行占用时占位；affected=0 即"正在执行" */
    int casManualRun(@Param("id") String id, @Param("instanceId") String instanceId);

    /** 手动执行结束释放占用（仅当占用者仍是本实例） */
    int releaseManualRun(@Param("id") String id, @Param("instanceId") String instanceId);

    /** 终止运行：日志行 CANCELLED 由服务层写，这里把任务行拉回可调度态 */
    int terminateRunning(@Param("id") String id, @Param("instanceId") String instanceId,
                         @Param("nextFireTime") Date nextFireTime,
                         @Param("pendingNextFireTime") Date pendingNextFireTime, @Param("once") boolean once);

    // ===== 生命周期清理（§7.8）=====

    /** 一次性 DONE/CANCELLED 保留 N 天后物理删（LIMIT 分批循环） */
    int purgeFinishedOnce(@Param("retainDays") int retainDays, @Param("limit") int limit);

    /** P2-6：配额达 90% 时的应急清理——只删该组织最老的终态一次性任务，不等保留期 */
    int purgeOldestFinishedByOrg(@Param("orgId") String orgId, @Param("limit") int limit);

    /** 日志：删 N 天前数据（删前搬非 SUCCESS 进归档表，§6.3） */
    int archiveOldLogs(@Param("retainDays") int retainDays, @Param("limit") int limit);

    int deleteArchivedLogs(@Param("retainDays") int retainDays, @Param("limit") int limit);

    // ===== 注册/改期/取消（§12.2，内部 API 落点）=====

    int insertTask(SystemScheduleTask task);

    /** upsert 覆盖分支：READY/PAUSED 覆盖改期（PAUSED 保持暂停）；version+1 */
    int upsertOverwrite(SystemScheduleTask task);

    /** DONE/CANCELLED 复活：置回 READY、重置 attempt/fail_count/trigger_count/last_error（注4） */
    int upsertRevive(SystemScheduleTask task);

    /** 改期（仅 READY/PAUSED 生效；TRIGGERED/RUNNING 不改行，§7.5） */
    int reschedule(@Param("id") String id, @Param("newFireTime") Date newFireTime);

    /** P1-4：在途编辑/启停登记到 pending_change（仅 TRIGGERED/RUNNING 行生效，避免幽灵变更） */
    int updatePendingChange(@Param("id") String id, @Param("pendingChange") String pendingChange);

    /** P1-4：解析失败等异常情形下丢弃暂存变更，避免每轮回写都重试 */
    int clearPendingChange(@Param("id") String id);

    /** P1-5 配套：在途行的取消意图登记（精确 bizKey） */
    int stageCancelInFlightByBizKey(@Param("orgId") String orgId, @Param("source") String source,
                                    @Param("bizKey") String bizKey);

    /** P1-5 配套：在途行的取消意图登记（前缀，供删表单/删规则级联用） */
    int stageCancelInFlightByPrefix(@Param("orgId") String orgId, @Param("source") String source,
                                    @Param("bizPrefix") String bizPrefix);

    /** 软取消：置 CANCELLED 不置 deleted_flag（同 bizKey 再注册走复活，§6.1 注4） */
    int cancelByBizKey(@Param("orgId") String orgId, @Param("source") String source, @Param("bizKey") String bizKey);

    int cancelByPrefix(@Param("orgId") String orgId, @Param("source") String source, @Param("bizPrefix") String bizPrefix);

    int pauseByAppId(@Param("orgId") String orgId, @Param("appId") String appId);

    /** 组织/应用活跃任务计数（配额统计，§11） */
    long countActive(@Param("orgId") String orgId, @Param("appId") String appId);

    /** 分应用活跃任务明细（配额视图 T14；返回 app_id/appName/active） */
    java.util.List<Map<String, Object>> countActiveByApp(@Param("orgId") String orgId);

    // ===== QUEUE 并发策略（§7.4 P2：错过轮次落库排队）=====

    /** 入队（INSERT IGNORE，uk_task_fire 防重） */
    int enqueueQueue(@Param("taskId") String taskId, @Param("fireTime") Date fireTime);

    /** 最早 PENDING 队行时刻（无则 null） */
    Date pollEarliestQueue(@Param("taskId") String taskId);

    /** 消费队首（出队即被任务行 next_fire_time 承载） */
    int consumeQueue(@Param("taskId") String taskId, @Param("fireTime") Date fireTime);

    /** P1-7：QUEUE 残留清理（任务已删/已终态的排队行、以及超期补跑轮次），由 cleanupTick 调用 */
    int purgeStaleQueue(@Param("retainDays") int retainDays, @Param("limit") int limit);

    // ===== 管理面写操作（按 id 精确操作，服务层 @PreAuthorize 把关，§9.3）=====

    /** 软删 + CANCEL：deleted_time 取当前时刻参与唯一键释放（§6.1 注4；datetime(3) 防同秒删建撞回） */
    int softDeleteCancel(@Param("ids") List<String> ids, @Param("operator") String operator,
                         @Param("organizationId") String organizationId);

    /** 启用/暂停（version+1；启用时 next_fire_time 已由服务层重算传入） */
    int changeStatus(@Param("id") String id, @Param("status") String status,
                     @Param("nextFireTime") Date nextFireTime);

    /** 按应用/来源批量启停（§12.1） */
    int batchChangeStatus(@Param("orgId") String orgId, @Param("appId") String appId,
                          @Param("source") String source, @Param("status") String status);
}
