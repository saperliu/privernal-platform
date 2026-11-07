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

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单联动规则：规则 = 1 个事件 + 1 棵条件树 + 有序动作列表。
 * rule_code 是应用内稳定编码（调度 biz_key 与应用包判重都用它，不用 id）。
 * JSON 列以 String 承载（JSONObjectTypeHandler 只支持对象，顶层数组会炸；条件树可能为 null 语义=恒真）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dynamic_linkage_rule", autoResultMap = true)
public class DynamicLinkageRule extends BaseEntity {
    private static final long serialVersionUID = 1L;

    public static final String EVENT_CREATE = "CREATE";
    public static final String EVENT_UPDATE = "UPDATE";
    public static final String EVENT_DELETE = "DELETE";
    public static final String EVENT_EFFECTIVE = "EFFECTIVE";
    public static final String EVENT_TIMER = "TIMER";
    public static final String EVENT_DATE_FIELD = "DATE_FIELD";

    public static final String ACTION_DATA_UPDATE = "DATA_UPDATE";
    public static final String ACTION_DATA_CREATE = "DATA_CREATE";
    public static final String ACTION_DATA_DELETE = "DATA_DELETE";
    public static final String ACTION_MSG_INNER = "MSG_INNER";

    public static final String MODE_ASYNC = "ASYNC";
    public static final String MODE_SYNC = "SYNC";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String departmentId;

    private String organizationId;

    /** 关联 dynamic_form.id */
    private String formId;

    /** 应用内稳定编码 [a-z][a-z0-9_]{2,63}，表单内唯一 */
    private String ruleCode;

    /** 规则名称 */
    private String name;

    /** 启用 Y/N */
    private String enabled;

    /** CREATE/UPDATE/DELETE/EFFECTIVE（TIMER/DATE_FIELD 待调度内核，§7.7） */
    private String eventType;

    /** JSON：UPDATE:{watchFields[]}；TIMER:{cron}；DATE_FIELD:{fieldCode,offsetDays} 预留 */
    private String eventConfig;

    /** 条件树 JSON，NULL=恒真（§7.3） */
    private String conditionTree;

    /** 有序动作数组 JSON [{type,config,stopOnFail}] */
    private String actions;

    /** ASYNC 事件后异步（默认）/ SYNC 同事务（仅 DATA_* 动作，§7.5.2） */
    private String asyncMode;

    /** CONTINUE 继续后续动作 / ABORT 中止并告警 */
    private String failPolicy;

    /** 允许的级联触发深度，默认 1=本规则触发的变更不再触发联动（§7.5.3） */
    private Integer maxDepth;

    /** TIMER 作用对象 PER_ROW/WHOLE_TABLE（P2 预留） */
    private String targetMode;

    /** 漏触发 COMPENSATE/SKIP（P2 预留） */
    private String misfirePolicy;

    /** 重叠执行 FORBID/QUEUE（P2 预留） */
    private String concurrentPolicy;

    /** 单轮执行上限秒 */
    private Integer timeoutSeconds;

    /** 单次触发最多处理行数 */
    private Integer maxRowsPerFire;
}
