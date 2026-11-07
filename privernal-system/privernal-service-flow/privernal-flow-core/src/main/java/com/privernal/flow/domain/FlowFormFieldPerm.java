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

/**
 * 流程表单节点字段权限明细对象 flow_form_field_perm
 * 按版本+节点维度控制字段显隐/可编辑/必填，node_code=global 为全局默认配置
 * （替代旧 wfs 的 workflow_instance_form_field_task / _task_release）
 *
 * @author saperliu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_form_field_perm")
public class FlowFormFieldPerm extends BaseEntity {

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

    /** 版本主表id */
    @TableField("version_id")
    private String versionId;

    /** 流程定义编码 */
    @TableField("flow_code")
    private String flowCode;

    /** 版本号 */
    @TableField("version")
    private Integer version;

    /** 节点编码，global 表示全局默认配置 */
    @TableField("node_code")
    private String nodeCode;

    /** 字段编码（dynamic_form_column.filed_code） */
    @TableField("field_code")
    private String fieldCode;

    /** 字段名称 */
    @TableField("field_name")
    private String fieldName;

    /** 是否显示 0否 1是 */
    @TableField("is_show")
    private Integer isShow;

    /** 是否可编辑 0否 1是 */
    @TableField("is_edit")
    private Integer isEdit;

    /** 是否必填 0否 1是 */
    @TableField("is_required")
    private Integer isRequired;

    /** 默认值类型 */
    @TableField("default_type")
    private String defaultType;

    /** 默认值 */
    @TableField("default_value")
    private String defaultValue;

    /** 字典类型 */
    @TableField("dict_type")
    private String dictType;

    /** 排序 */
    @TableField("sort")
    private Integer sort;
}
