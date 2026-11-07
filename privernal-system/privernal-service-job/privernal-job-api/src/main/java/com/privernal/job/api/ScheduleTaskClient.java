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

package com.privernal.job.api;

import java.util.Date;
import java.util.List;

/**
 * 定时任务模块对内 API。
 *
 * <p>供 warm-flow 同步器、表单设计器、数据到期钩子、应用包导入等内部调用方使用。
 * 所有签名一律带 orgId：这些调用大多发生在异步/免登链路，没有会话上下文可依赖。</p>
 *
 * <p>register 幂等语义（§12.2 upsert）：命中同 (orgId, source, bizKey) 时不报错——
 * READY/PAUSED 覆盖改期（PAUSED 保持暂停，由管理员启用）；TRIGGERED/RUNNING 不改行、
 * 返回 deferred=true；DONE/CANCELLED 复活为 READY 并重置计数。</p>
 *
 * @author saperliu
 */
public interface ScheduleTaskClient {

    /**
     * 注册一次性任务（WORKFLOW 超时、DESIGNER_DATA 数据到期提醒等）。
     *
     * @param orgId        组织 id（隔离/配额维度）
     * @param source       来源 WORKFLOW/DESIGNER/DESIGNER_DATA/SYSTEM/OPENAPI
     * @param bizKey       业务键，如 wf:ins:{insId}:task:{taskId}、linkdate:{ruleId}:{dataId}
     * @param fireTime     计划触发时间
     * @param actionType   动作类型（§3.2）
     * @param actionConfig 动作参数（会序列化为 json 快照）
     * @param bizContext   业务上下文快照（一次性任务不依赖业务现值）
     * @param databaseName 业务库名快照（执行时优先按 orgId 现场解析，取不到才用它兜底）
     */
    TaskRef registerOnce(String orgId, String source, String bizKey, Date fireTime,
                         String actionType, Object actionConfig, Object bizContext, String databaseName);

    /**
     * 注册周期任务（设计器"定时触发器"等）。命中同 (orgId, source, bizKey) 为幂等改期。
     */
    TaskRef registerCron(String orgId, String source, String bizKey, String taskCode, String cron,
                         String actionType, Object actionConfig, String databaseName);

    /**
     * 带应用归属的周期任务注册（应用包导入 §9.1：新行落 app_id，保证目标环境按应用再导出仍含任务；
     * 行已存在时归属不变）。其余语义与 {@link #registerCron} 一致。
     */
    TaskRef registerCronWithApp(String orgId, String appId, String source, String bizKey, String taskCode,
                                String cron, String actionType, Object actionConfig, String databaseName);

    /**
     * 带应用归属的一次性任务注册（应用包导入 §9.1；语义同 {@link #registerOnce}，新行落 app_id）。
     */
    TaskRef registerOnceWithApp(String orgId, String appId, String source, String bizKey, Date fireTime,
                                String actionType, Object actionConfig, Object bizContext, String databaseName);

    /** 注册固定间隔任务（SYSTEM 存量迁入等；最小间隔 60s；命中同键幂等改期） */
    TaskRef registerFixedDelay(String orgId, String source, String bizKey, String taskCode,
                               int intervalSeconds, String actionType, Object actionConfig, String databaseName);

    /** 改期：仅 READY/PAUSED 行生效；TRIGGERED/RUNNING 行不改（延迟生效由调用方重试，§7.5） */
    boolean reschedule(String orgId, String source, String bizKey, Date newFireTime);

    /** 取消（软取消：置 CANCELLED，不置 deleted_flag，同 bizKey 再注册走复活分支，§6.1 注4） */
    boolean cancel(String orgId, String source, String bizKey);

    /** 按业务键前缀取消（表单/规则/应用/流程实例级联，§7.8） */
    int cancelByPrefix(String orgId, String source, String bizPrefix);

    /** 按业务键前缀列出现存行的 biz_key（Syncer 对账：现有注册 vs 引擎现状，§10.1） */
    java.util.List<String> listActiveBizKeys(String orgId, String source, String bizPrefix);

    /** 按业务键查任务快照（OpenAPI 查询通道 §9.2）；不存在返回 null */
    java.util.Map<String, Object> selectByBizKey(String orgId, String source, String bizKey);

    /** 应用级联暂停（应用下架挂点，由调用方显式调用，§9.1 第7条） */
    int pauseByApp(String orgId, String appId);

    /** 未来 n 次触发预览（唯一不带 orgId 的方法，纯算表达式；jitter 走确定性 hash，结果稳定） */
    List<Date> previewNext(String cron, int n);
}
