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
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * form的列的数据对象 dynamic_form_column
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description form的列的数据的实体
 */
@TableName(value = "dynamic_form_column", autoResultMap = true)
@Data
public class DynamicFormColumn extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 所属部门
     */
    private String departmentId;
    /**
     * 所属组织
     */
    private String organizationId;
    /**
     * 数据标题
     */
    private String title;
    /**
     * 状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消
     */
    private Long status;
    /**
     * 表单ID
     */
    private String formId;
    /**
     * 字段编码,数据库表的列表
     */
    private String filedCode;
    /**
     * 字段名称
     */
    private String filedName;
    /**
     * 字段类型
     */
    private String filedType;

    /**
     * 前端组件的ID
     */
    private String componentId;

    /**
     * 长度
     */
    private Long length;
    /**
     * 小数点位数
     */
    private Long scale;
    /**
     * 排序
     */
    private Long sort;
    /**
     * 默认值
     */
    private String defaultValue;
    /**
     * 查询条件的默认值；列表查询面板自动预填该值
     */
    private String queryDefaultValue;
    /**
     * 列表默认排序方向；ASC/DESC，空为不参与默认排序
     */
    private String orderDir;
    /**
     * 列表是否显示
     */
    private Long showList;
    /**
     * 查询是否显示
     */
    private Long showQuery;

    /**
 * 敏感字段标记：导出/打印/外链按 io_config.sensitive_policy 脱敏或隐藏。
     * 列级配置的真相就在本列（不进 schema）；列记录重建时必须保留旧值。
     * 列名避开 MySQL 新版保留字 sensitive，落库为 sensitive_flag。
     */
    @com.baomidou.mybatisplus.annotation.TableField("sensitive_flag")
    private String sensitive;

    /**
     * 业务唯一键标记：由组件属性 props.unique 物化的只读镜像（真相在 meta_data.props），
     * 表单每次保存重建列记录时随 props 刷新；导入查重/模板生成在 SQL 层读它。
     */
    private String uniqueFlag;


    /**
     * 系统级公共字段编码：与 FormTransferTable 建表基础列一致（外加主键 id）。
     * 这些字段的定义锁定不可修改，保存数据的默认值不可配置，查询条件的默认值可配置。
     */
    public static final java.util.Set<String> SYSTEM_FIELD_CODES = java.util.Set.of(
            "id", "tenant_id", "revision", "created_by", "created_time", "updated_by", "updated_time",
            "deleted_flag", "deleted_by", "deleted_time", "department_id", "organization_id",
            "parent_id", "remark", "status", "title");

    /**
     * 是否系统级公共字段
     *
     * @param filedCode 列编码
     * @return true=系统字段（定义与保存默认值锁定）
     */
    public static boolean isSystemField(String filedCode) {
        return filedCode != null && SYSTEM_FIELD_CODES.contains(filedCode);
    }


}
