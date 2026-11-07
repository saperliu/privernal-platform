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

package com.privernal.job.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 定时任务执行日志 system_schedule_task_log。
 *
 * <p>全量落库（不采样，保证重试链完整）；uk_idem(task_id, fire_time, attempt) 兼作幂等锁；
 * attempt=0 固定表示跳过类记录（SKIPPED_*），与执行链（从 1 递增）不撞唯一键。</p>
 *
 * @author saperliu
 */
@Data
@TableName("system_schedule_task_log")
public class SystemScheduleTaskLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String taskId;

    private String organizationId;

    private String appId;

    private String source;

    /** 计划触发时间（未来分区键），时间筛选一律用此列 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date fireTime;

    /** 第几次尝试；跳过类固定 0 */
    private Integer attempt;

    private Long taskVersion;

    /** 执行时的 action_config 快照（json 串），保证事后可复现 */
    private String actionSnapshot;

    /** RUNNING/SUCCESS/FAILED/TIMEOUT/SKIPPED_MISFIRE/SKIPPED_CONCURRENT/SKIPPED_POOL/CANCELLED */
    private String status;

    private String executor;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date endTime;

    private Integer costMs;

    private String errorMsg;

    private String resultDigest;

    private String traceId;
}
