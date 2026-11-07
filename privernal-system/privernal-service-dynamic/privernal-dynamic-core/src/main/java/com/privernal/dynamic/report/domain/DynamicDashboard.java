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
 * 报表仪表盘 dynamic_dashboard
 *
 * @author saperliu
 */
@Table(name = "dynamic_dashboard")
@TableName(value = "dynamic_dashboard", autoResultMap = true)
@Data
public class DynamicDashboard extends BaseEntity {
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
     * 仪表盘编码（业务主键，菜单 report_{code} 与权限 report:{code}:view 用它）
     */
    @Column(name = "dashboard_code", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String dashboardCode;

    /**
     * 仪表盘名称
     */
    @Column(name = "dashboard_name", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String dashboardName;

    /**
     * 所属应用ID
     */
    @Column(name = "app_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String appId;

    /**
     * 布局配置（栅格明细在 item 表）
     */
    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "config", type = MySqlTypeConstant.JSON)
    private JSONObject config;
}
