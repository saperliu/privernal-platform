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

package com.privernal.dynamic.dba.constants;

/**
 * 动态表公共字段常量
 * <p>
 * 集中管理动态表中的系统公共字段名（如审计字段、租户字段、部门组织字段等），
 * 便于在 {@link com.privernal.dynamic.dba.utils.FormTransferTable} 等工具类中统一引用。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 * @see Constants
 */
public final class TableFieldConstant {

    private TableFieldConstant() {
    }

    /** 创建人字段 */
    public static final String FIELD_CREATED_BY = "created_by";
    /** 创建人名称字段 */
    public static final String FIELD_CREATED_NAME = "created_name";
    /** 租户 ID 字段 */
    public static final String FIELD_TENANT_ID = "tenant_id";
    /** 乐观锁字段 */
    public static final String FIELD_REVISION = "revision";
    /** 创建时间字段 */
    public static final String FIELD_CREATED_TIME = "created_time";
    /** 更新人字段 */
    public static final String FIELD_UPDATED_BY = "updated_by";
    /** 更新时间字段 */
    public static final String FIELD_UPDATED_TIME = "updated_time";
    /** 逻辑删除标记字段 */
    public static final String FIELD_DELETED_FLAG = "deleted_flag";
    /** 删除人字段 */
    public static final String FIELD_DELETED_BY = "deleted_by";
    /** 删除时间字段 */
    public static final String FIELD_DELETED_TIME = "deleted_time";
    /** 部门 ID 字段 */
    public static final String FIELD_DEPARTMENT_ID = "department_id";
    /** 部门名称字段 */
    public static final String FIELD_DEPARTMENT_NAME = "department_name";
    /** 组织 ID 字段 */
    public static final String FIELD_ORGANIZATION_ID = "organization_id";
    /** 备注字段 */
    public static final String FIELD_REMARK = "remark";
    /** 数据标题字段 */
    public static final String FIELD_TITLE = "title";
    /** 业务状态字段 */
    public static final String FIELD_STATUS = "status";
}