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

package com.privernal.dynamic.advanced.mapper;

import com.privernal.dynamic.advanced.domain.DynamicIoImportTask;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.common.datascope.annotation.PrivernalDataScope;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

/**
 * 导入任务持久化 Mapper（master 元数据库）。
 * 数据范围挂 dynamic:form（同 DynamicFormIoConfigMapper 口径）：PrivernalMapper 全系走
 * 数据范围切面，无注解会被 fail-closed 替换为 1=2（R2-19 实测踩坑）。
 * CAS 两条是 R2-19 多实例续跑的核心：UPDATE 的 WHERE 带状态/心跳条件，谁更新到谁是执行者。
 */
@PrivernalDataScope(permissionMain = "dynamic:form")
public interface DynamicIoImportTaskMapper extends PrivernalMapper<DynamicIoImportTask> {

    /**
     * 认领断点续跑：仅当任务仍 RUNNING 且心跳已过期（或从未写过心跳）时改写 worker。
     *
     * @return 1=认领成功（本实例成为执行者）；0=已被其他实例认领或状态已变
     */
    @Update("UPDATE dynamic_io_import_task SET worker_id = #{workerId}, heartbeat_time = #{heartbeat}, "
            + "revision = revision + 1 "
            + "WHERE id = #{taskId} AND status = 'RUNNING' "
            + "AND (heartbeat_time IS NULL OR heartbeat_time < #{staleBefore})")
    int claimStaleTask(@Param("taskId") String taskId,
                       @Param("workerId") String workerId,
                       @Param("staleBefore") Date staleBefore,
                       @Param("heartbeat") Date heartbeat);

    /**
     * 启动执行：仅 PENDING_MAPPING 可置 RUNNING（防重复点击 start 双跑）。
     *
     * @return 1=启动成功；0=任务已启动/状态不符
     */
    @Update("UPDATE dynamic_io_import_task SET status = 'RUNNING', worker_id = #{workerId}, "
            + "heartbeat_time = #{heartbeat}, revision = revision + 1 "
            + "WHERE id = #{taskId} AND status = 'PENDING_MAPPING'")
    int startPendingTask(@Param("taskId") String taskId,
                         @Param("workerId") String workerId,
                         @Param("heartbeat") Date heartbeat);
}
