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

package com.privernal.dynamic.advanced.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 联动规则执行日志。append-only 大表，无软删列。
 * uk_idem(rule_id, event_id) 是幂等的物理保证（§7.5.1）：重复事件在此撞键即跳过；
 * 先插日志再执行动作，进程在执行中崩溃时重投会撞键——继承事件管道"宁漏不幽灵"取舍。
 */
@Data
@TableName(value = "dynamic_linkage_log")
public class DynamicLinkageLog implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_SKIPPED_CONCURRENT = "SKIPPED_CONCURRENT";
    public static final String STATUS_DUPLICATE = "DUPLICATE";

    /** 执行中的占位状态：先插行再执行动作，崩溃后该行保持此标记且重投撞键（at-most-once） */
    public static final String STATUS_EXECUTING = "EXECUTING";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String ruleId;

    private String formId;

    /** 规则编码冗余（规则删除后日志仍可读） */
    private String ruleCode;

    /** 隔离维度是组织，不用 tenant_id（基线常量） */
    private String organizationId;

    /** 业务库名，异步重放上下文用 */
    private String databaseName;

    private String eventType;

    private String dataId;

    /** 幂等键，§7.5.1 口径，禁止随机 UUID */
    private String eventId;

    /** 计划触发时刻（TIMER/DATE_FIELD 参与事件） */
    private Date fireTime;

    /** NORMAL/OPENAPI/PUBLIC_LINK/IMPORT/LINKAGE */
    private String sourceTag;

    /** 级联深度路径 */
    private Integer depth;

    /** 条件判定 Y/N */
    private String conditionPass;

    /** 条件判定依据（排查"为什么没触发"） */
    private String conditionTrace;

    private String status;

    /** 逐动作结果 JSON [{type,status,costMs,error,attempts}] */
    private String actionResults;

    private String errorMsg;

    private Date startTime;

    private Integer costMs;
}
