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

package com.privernal.flow.mapper;

import com.privernal.common.datascope.annotation.IgnoreDataScope;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * warm-flow 超时任务迁移的跨表访问。
 *
 * <p>两件事：① system_schedule_lock 的每日兜底锁（替代 Redisson，§7.3）；
 * ② §10.5 兜底条件改造——"有配置"必须同时"system_schedule_task 里存在未终态的对应行"，
 * 否则 syncer 漏注册的待办会被 48h 兜底永久跳过且无告警。</p>
 *
 * <p>类上 @IgnoreDataScope：com.privernal. 前缀 mapper 无注解时数据权限切面会把 WHERE 整段
 * 替换为 1 = 2（UPDATE 也改写且不报错），兜底锁永远拿不到；内核线程另有线程级豁免双保险。</p>
 *
 * @author saperliu
 */
@IgnoreDataScope
public interface FlowTimeoutTaskMapper {

    /** 抢每日兜底锁：holder 是本实例或租约已过期时续期；affected=1 即本次可执行 */
    int tryReconcileLock(@Param("holder") String holder, @Param("leaseSeconds") int leaseSeconds);

    /** 释放锁（处理完成后立即释放，不等租约到期） */
    int releaseReconcileLock(@Param("holder") String holder);

    /** 该待办是否存在未终态（READY/PAUSED/TRIGGERED/RUNNING）的超时任务行 */
    int existsActiveTask(@Param("bizKey") String bizKey);

    /**
     * 该待办是否**存在过任何**超时任务行（不限状态，含 DONE/CANCELLED）。
     * P0-8：NOTIFY 判定必须用它——一次性任务发完即 DONE，按未终态判断会导致每日兜底重发提醒。
     */
    int existsAnyTask(@Param("bizKey") String bizKey);

    /** 兜底处置前把实例的全部遗留行取消（实例已办结时，防悬挂任务到点空跑） */
    int cancelByPrefix(@Param("orgId") String orgId, @Param("bizPrefix") String bizPrefix,
                       @Param("beforeFireTime") Date beforeFireTime);
}
