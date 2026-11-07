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
 * 表单数据导入导出配置。一表单一行，uk_form(form_id)。
 * 运营态实时配置，不进 metaData 版本快照、不随发布/回滚（§3.2）。
 * JSON 数组列（export_columns）以 String 承载——JSONObjectTypeHandler 只支持对象，顶层数组会炸。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dynamic_form_io_config", autoResultMap = true)
public class DynamicFormIoConfig extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String departmentId;

    private String organizationId;

    /** 关联 dynamic_form.id */
    private String formId;

    /** 允许数据导出 Y/N */
    private String exportEnabled;

    /** 允许数据导入 Y/N */
    private String importEnabled;

    /** 导出范围 SELF/DEPT/ALL（叠加行级数据范围取交集，不放大权限） */
    private String exportScope;

    /** 导出字段编码数组 JSON，空/空数组=跟随 show_list */
    private String exportColumns;

    /** 敏感列策略 MASK/HIDE（载体 dynamic_form_column.sensitive，§4.4 待落地） */
    private String sensitivePolicy;

    /** 导入重复键策略 SKIP/OVERWRITE/REJECT */
    private String dupStrategy;

    /** 导入必填/类型校验 Y/N */
    private String strictValidate;

    /** 单次导入行数上限 */
    private Integer importLimit;

    /** 导入执行方式 Y异步/N同步 */
    private String importAsync;

    /** 导入是否逐行执行 backScript Y/N */
    private String runBackscript;

    /** 导入是否发联动事件 SUPPRESS/FIRE */
    private String triggerMode;

    /** 工作流表单导入 NONE/AUTO_START */
    private String importFlowMode;
}
