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

package com.privernal.common.core.constant;

/**
 * 框架级数据约定：数据类型、启用状态、逻辑删除标记与子表操作类型。
 *
 * @author saperliu
 */
public class DataConstants
{
    /**
     * 属性复制（DTO ↔ 实体）时一律忽略的字段：租户号、乐观锁版本与逻辑删除标记。
     * 这些列由框架在写入时统一维护，不允许被入参覆盖。
     */
    public static final String[] IGNORED_ON_COPY = {"tenantId", "revision", "deletedFlag", "deletedBy", "deletedTime"};

    private DataConstants()
    {
    }

    /**
     * 数据类型，为系统默认，不允许删除
     */
    public static final String DATA_TYPE_SYSTEM = "SYSTEM";

    /**
     * 表示是否类型数据的状态
     * 1表示正常，0表示否
     */
    public static final Long DATA_STATUS_YES = 1L;

    public static final Long DATA_STATUS_NOT = 0L;

    public static final String DATA_DELETE_FLAG_YES = "Y";

    /**
     * 数据是否删除
     */
    public static final String DATA_DELETE_FLAG_NOT = "N";

    /**
     * 子表操作的类型，根据类型处理子表数据
     *
     */
    public static final String ACTION_TAG_DELETE = "DELETE";

    public static final String ACTION_TAG_EDIT = "EDIT";

    public static final String ACTION_TAG_ADD = "ADD";
}
