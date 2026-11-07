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

package com.privernal.dynamic.datav.domain;

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
 * 数据大屏 dynamic_datav_screen
 * <p>
 * 画布与组件全部以 schema_json 整体存储：编辑器一次保存一次提交，不做按组件拆行——
 * 拆行会让「撤销一次」变成多行事务，且组件私有样式无法枚举成固定列。
 * 组件数量、参数数量这类列表要用的统计值冗余成列，避免列表页解析全部画布 JSON。
 *
 * @author saperliu
 */
@Table(name = "dynamic_datav_screen")
@TableName(value = "dynamic_datav_screen", autoResultMap = true)
@Data
public class DynamicDatavScreen extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Column(name = "id", type = MySqlTypeConstant.VARCHAR, isKey = true, length = 36)
    private String id;

    @Column(name = "department_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String departmentId;

    @Column(name = "organization_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String organizationId;

    /**
     * 大屏编码（业务主键，跨环境可移植，应用包按此导入导出）
     */
    @Column(name = "screen_code", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String screenCode;

    @Column(name = "screen_name", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String screenName;

    @Column(name = "app_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String appId;

    /**
     * absolute 自由布局（默认）/ grid 栅格吸附
     */
    @Column(name = "layout_mode", type = MySqlTypeConstant.VARCHAR, length = 20)
    private String layoutMode;

    /**
     * 画布 schema：{schemaVersion, canvas, params, widgets, groups}
     */
    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "schema_json", type = MySqlTypeConstant.MEDIUMTEXT)
    private JSONObject schemaJson;

    /**
     * 0 草稿 1 已发布 2 已下线
     */
    @Column(name = "status", type = MySqlTypeConstant.INT)
    private Integer status;

    @Column(name = "thumbnail", type = MySqlTypeConstant.VARCHAR, length = 255)
    private String thumbnail;

    /**
     * 组件数冗余列，列表页用它显示而不解析 schema
     */
    @Column(name = "widget_count", type = MySqlTypeConstant.INT)
    private Integer widgetCount;
}
