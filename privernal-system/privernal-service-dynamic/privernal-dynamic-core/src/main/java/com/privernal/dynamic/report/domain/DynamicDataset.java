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
 * 报表数据集 dynamic_dataset
 * <p>
 * 一个数据集 = 一种数据源（默认绑动态表，也可 HTTP / JDBC / WebSocket / Excel）+ 列快照 + 默认过滤。
 * 图表永不绑表，只引用数据集编码：
 * 前缀变更、分库变更、字段治理都在数据集一处处理。列快照在建数据集时从 dynamic_form_column
 * 解析一次落库，运行时不再解析表单元数据。
 *
 * @author saperliu
 */
@Table(name = "dynamic_dataset")
@TableName(value = "dynamic_dataset", autoResultMap = true)
@Data
public class DynamicDataset extends BaseEntity {
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
     * 数据集编码（业务主键，跨环境可移植）
     */
    @Column(name = "dataset_code", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String datasetCode;

    /**
     * 数据集名称
     */
    @Column(name = "dataset_name", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String datasetName;

    /**
     * 逻辑表编码（不带组织前缀，与 dynamic_form.table_code 同源）
     */
    @Column(name = "table_code", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String tableCode;

    /**
     * 所属应用ID
     */
    @Column(name = "app_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String appId;

    /**
     * 列快照 [{fieldCode, fieldName, dataType(number|date|text)}]
     */
    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "column_snapshot", type = MySqlTypeConstant.JSON)
    private JSONObject columnSnapshot;

    /**
     * 默认过滤 [{field, op, value}]（结构化条件，非 SQL 文本）
     */
    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "default_filter", type = MySqlTypeConstant.JSON)
    private JSONObject defaultFilter;

    /**
     * 数据源类型：dynamic_table（默认，绑动态表单）/ http / jdbc / websocket / excel / static
     * <p>
     * 下游图表只消费 columnSnapshot，不感知此字段——所以新增数据源不需要改任何图表配置。
     */
    @Column(name = "source_type", type = MySqlTypeConstant.VARCHAR, length = 20)
    private String sourceType;

    /**
     * 数据源配置，按 sourceType 解释；外部凭据只存引用 ID，不落明文
     */
    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "source_config", type = MySqlTypeConstant.JSON)
    private JSONObject sourceConfig;

    /**
     * 刷新与缓存策略：{ttlSec, strategy, onError}
     */
    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "refresh_config", type = MySqlTypeConstant.JSON)
    private JSONObject refreshConfig;

    /**
     * 派生列定义 [{code, name, dataType, expr}]，表达式在服务端沙箱求值
     */
    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "computed_columns", type = MySqlTypeConstant.JSON)
    private JSONObject computedColumns;
}
