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
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 定时任务定义与调度状态 system_schedule_task。
 *
 * <p>不继承 BaseEntity：本表 deleted_time 是"NOT NULL 纪元值默认 + 参与唯一键"的有意偏离（注4），
 * 审计列由代码显式赋值，避免通用填充/软删机制干扰内核 SQL；也未启用 @TableLogic，
 * 全部访问走本模块 XML 中的显式 SQL（内核与管理两侧都是）。</p>
 *
 * @author saperliu
 */
@Data
@TableName("system_schedule_task")
public class SystemScheduleTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String tenantId;

    private Long revision;

    @TableField("created_by")
    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    @TableField("created_time")
    private Date createdTime;

    @TableField("updated_by")
    private String updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    @TableField("updated_time")
    private Date updatedTime;

    private String deletedFlag;

    private String deletedBy;

    /** 删除时刻；未删固定为 1970-01-01 纪元值，参与唯一键（§6.1 注4） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date deletedTime;

    private String departmentId;

    private String organizationId;

    private String remark;

    /** 应用内稳定编码，随应用包迁移；ONCE 可空 */
    private String taskCode;

    private String title;

    private String appId;

    /** WORKFLOW/DESIGNER/DESIGNER_DATA/SYSTEM/OPENAPI/MANUAL */
    private String source;

    /** 业务关联键；无键时代码填 uuid 保证唯一索引生效（注1） */
    private String bizKey;

    /** CRON/FIXED_DELAY/ONCE */
    private String triggerType;

    private String cronExpr;

    private Integer intervalSeconds;

    private Long periodSeconds;

    private Integer jitterSeconds;

    /** COMPENSATE/SKIP */
    private String misfirePolicy;

    private Integer misfireWindowSeconds;

    /** FORBID/SKIP；QUEUE 未启用（§7.4） */
    private String concurrentPolicy;

    private Integer timeoutSeconds;

    private Integer maxRetry;

    private String actionType;

    /** 动作参数（json）；表名一律存物理名（§8.3 第4项） */
    private String actionConfig;

    /** 注册时上下文快照（json） */
    private String bizContext;

    /** 业务库名注册时快照；执行时优先按 organization_id 现场解析（§8.3 第3项） */
    private String databaseName;

    private String callbackUrl;

    /** READY/PAUSED/TRIGGERED/RUNNING/DONE/CANCELLED */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date nextFireTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date pendingNextFireTime;

    /** P1-4：在途（TRIGGERED/RUNNING）期间的编辑/启停暂存 json，本轮回写后应用并清空 */
    private String pendingChange;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date lastFireTime;

    private String ownerInstance;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date leaseUntil;

    private String runningInstance;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date runningSince;

    private Long triggerCount;

    private Long failCount;

    private String lastError;

    /** 配置版本号：仅用户改配置时 +1，调度抢占不改（注2） */
    private Long version;

    /** 当前执行轮次（幂等键 attempt 来源）：正常触发为 1，失败重试递增，成功/跳过后重置 */
    private Integer currentAttempt;
}
