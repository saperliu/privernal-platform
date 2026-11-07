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

package com.privernal.dynamic.dbs.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 动态表单关联引用注册对象 dynamic_form_relation
 * 表单保存时把 meta_data 里的关联配置（relation.formCode）解析成「引用方表单 → 被关联表单」登记，
 * 关联显示数据的读取授权按它判定，不再扫描 meta_data
 *
 * @author saperliu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dynamic_form_relation")
public class DynamicFormRelation extends BaseEntity {

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

    /** 引用方表单主键（dynamic_form.id） */
    @TableField("form_id")
    private String formId;

    /** 引用方表单编码（dynamic_form.table_code） */
    @TableField("table_code")
    private String tableCode;

    /** 被关联表单编码（relation.formCode） */
    @TableField("target_table_code")
    private String targetTableCode;

    /** 引用到的被关联表字段，逗号分隔（displayField + mappings.field） */
    @TableField("return_columns")
    private String returnColumns;

    /** 任一引用配置了数据范围 0否 1是；配置后读取不收敛列（范围表达式在前端按返回行求值） */
    @TableField("has_filter")
    private Integer hasFilter;
}
