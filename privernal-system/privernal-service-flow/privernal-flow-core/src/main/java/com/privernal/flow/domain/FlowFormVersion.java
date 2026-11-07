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

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 流程表单配置版本对象 flow_form_version
 * 流程与动态表单的绑定关系 + 版本控制（草稿/发布），替代旧 wfs 的 workflow_instance_form
 * 及 _task/_task_release 双表机制（草稿=未发布版本，发布快照=已发布版本，支持多版本与回滚）
 *
 * @author saperliu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_form_version")
public class FlowFormVersion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 组织 */
    @TableField("organization_id")
    private String organizationId;

    /** 部门 */
    @TableField("department_id")
    private String departmentId;

    /** 流程定义编码（warm-flow flow_definition.flow_code） */
    @TableField("flow_code")
    private String flowCode;

    /** 流程名称 */
    @TableField("flow_name")
    private String flowName;

    /** 动态表单编码（dynamic_form.table_code） */
    @TableField("table_code")
    private String tableCode;

    /** 表单名称 */
    @TableField("form_name")
    private String formName;

    /** 版本号（同 flow_code 下递增，从1开始） */
    @TableField("version")
    private Integer version;

    /** 发布状态 0草稿 1已发布 */
    @TableField("publish_status")
    private Integer publishStatus;

    /** 是否当前生效发布版 Y/N */
    @TableField("current_flag")
    private String currentFlag;

    /** 发布时间 */
    @TableField("publish_time")
    private Date publishTime;

    /** 发布人 */
    @TableField("publish_by")
    private String publishBy;
}
