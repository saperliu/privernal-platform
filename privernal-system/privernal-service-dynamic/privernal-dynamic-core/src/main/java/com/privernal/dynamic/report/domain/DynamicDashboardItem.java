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
 * 仪表盘卡片 dynamic_dashboard_item
 * <p>
 * 单独立表而不是塞进 dashboard 的 JSON：删除图表/仪表盘时要可靠反查引用
 * （与 form_relation 注册表同一教训，不踩第二次）。
 *
 * @author saperliu
 */
@Table(name = "dynamic_dashboard_item")
@TableName(value = "dynamic_dashboard_item", autoResultMap = true)
@Data
public class DynamicDashboardItem extends BaseEntity {
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
     * 仪表盘编码
     */
    @Column(name = "dashboard_code", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String dashboardCode;

    /**
     * 图表编码
     */
    @Column(name = "report_code", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String reportCode;

    /**
     * 卡片标题（缺省用图表名）
     */
    @Column(name = "title", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String title;

    /**
     * 栅格跨度 1-12
     */
    @Column(name = "span", type = MySqlTypeConstant.INT, length = 11)
    private Integer span;

    /**
     * 排序号
     */
    @Column(name = "order_num", type = MySqlTypeConstant.INT, length = 11)
    private Integer orderNum;

    /**
     * 卡片样式配置
     */
    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "config", type = MySqlTypeConstant.JSON)
    private JSONObject config;
}
