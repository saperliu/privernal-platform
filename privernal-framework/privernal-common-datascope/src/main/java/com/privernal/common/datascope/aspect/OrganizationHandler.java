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

package com.privernal.common.datascope.aspect;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;

import java.util.List;

/**
 * 组织数据（ organizationId 行级 ）
 *
 * @author saperliu
 * @since 1.0.0
 */
public interface OrganizationHandler {

    /**
     * 获取组织 ID 值表达式，只支持单个 ID 值
     * <p>
     *
     * @return 组织 ID 值表达式
     */
    Expression getOrganizationId();

    /**
     * 获取组织字段名
     * <p>
     * 默认字段名叫: organization_id
     *
     * @return 组织字段名
     */
    default String getOrganizationIdColumn() {
        return "organization_id";
    }

    /**
     * 获取部门字段名
     * 默认字段名叫: department_id
     * @return 部门字段名
     */
    default String getDepartmentIdColumn() {
        return "department_id";
    }

    /**
     * 根据表名判断是否忽略拼接多组织条件
     * <p>
     * 默认都要进行解析并拼接多组织条件
     *
     * @param tableName 表名
     * @return 是否忽略, true:表示忽略，false:需要解析并拼接多组织条件
     */
    default boolean ignoreTable(String tableName) {
        return false;
    }

    /**
     * 忽略插入组织字段逻辑
     *
     * @param columns        插入字段
     * @param organizationIdColumn 组织 ID 字段
     * @return
     */
    default boolean ignoreInsert(List<Column> columns, String organizationIdColumn) {
        return columns.stream().map(Column::getColumnName).anyMatch(i -> i.equalsIgnoreCase(organizationIdColumn));
    }
}
