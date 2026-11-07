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

package com.privernal.dynamic.report.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.privernal.common.mybatis.type.JSONObjectTypeHandler;
import com.privernal.dynamic.dba.annotation.Column;
import com.privernal.dynamic.dba.annotation.Table;
import com.privernal.dynamic.dba.constants.MySqlTypeConstant;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 报表图表 dynamic_report
 * <p>
 * 图表引用数据集编码，config 内声明维度/度量/过滤/排序/上限：
 * {dimensions:[{field,granularity,lookup:{targetTableCode,displayField}}],
 * measures:[{field,agg}], filters:[{field,op,value}], sort:{field,asc}, limit}
 *
 * @author saperliu
 */
@Table(name = "dynamic_report")
@TableName(value = "dynamic_report", autoResultMap = true)
@Data
public class DynamicReport extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Column(name = "id", type = MySqlTypeConstant.VARCHAR, isKey = true, length = 36)
    private String id;

    /**
     * 所属部门
     */
    @Column(name = "department_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String departmentId;

    /**
     * 所属组织
     */
    @Column(name = "organization_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String organizationId;

    /**
     * 图表编码（业务主键）
     */
    @Column(name = "report_code", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String reportCode;

    /**
     * 图表名称
     */
    @Column(name = "report_name", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String reportName;

    /**
     * 图表类型：bar柱状 / pie饼图 / line折线 / stat数字卡
     */
    @Column(name = "report_type", type = MySqlTypeConstant.VARCHAR, length = 20)
    private String reportType;

    /**
     * 数据集编码
     */
    @Column(name = "dataset_code", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String datasetCode;

    /**
     * 所属应用ID
     */
    @Column(name = "app_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String appId;

    /**
     * 图表配置
     */
    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "config", type = MySqlTypeConstant.JSON)
    private JSONObject config;
}
