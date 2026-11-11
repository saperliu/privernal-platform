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

package com.privernal.flow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.privernal.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程父子实例关系对象 flow_subflow_rel（R2-18 子流程桥接）
 * <p>
 * warm-flow 1.8.9 引擎无原生子流程/Call Activity，平台侧桥接的映射表：
 * 父流程任务（ext.nodeKind=subflow 的节点）↔ 自动发起的子流程实例。
 * uk_parent_task 唯一约束防同一父任务重复发起（先插行后发起，插入冲突即并发双发）。
 * </p>
 *
 * @author saperliu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_subflow_rel")
public class FlowSubflowRel extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 关系状态：已发起等待子流程 */
    public static final String STATUS_LAUNCHED = "LAUNCHED";

    /** 关系状态：子流程办结，已回推父流程流转 */
    public static final String STATUS_DONE = "DONE";

    /** 关系状态：子流程已终止（父任务保持等待，由发起人人工处理） */
    public static final String STATUS_CHILD_TERMINATED = "CHILD_TERMINATED";

    /** 关系状态：子流程已撤销（父任务保持等待，由发起人人工处理） */
    public static final String STATUS_CHILD_REVOKED = "CHILD_REVOKED";

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 组织 */
    @TableField("organization_id")
    private String organizationId;

    /** 部门 */
    @TableField("department_id")
    private String departmentId;

    /** 父流程实例id */
    @TableField("parent_instance_id")
    private String parentInstanceId;

    /** 父流程子流程节点任务id */
    @TableField("parent_task_id")
    private String parentTaskId;

    /** 子流程实例id（发起成功后回填） */
    @TableField("child_instance_id")
    private String childInstanceId;

    /** 子流程编码（节点 ext.subflowCode 快照） */
    @TableField("subflow_code")
    private String subflowCode;

    /** 状态：LAUNCHED 已发起等待 / DONE 已回推 / CHILD_TERMINATED / CHILD_REVOKED */
    @TableField("status")
    private String status;

    /** 子流程结束时间 */
    @TableField("finished_time")
    private java.util.Date finishedTime;
}
