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

import java.util.Set;

/**
 * 动态表结构操作相关常量
 * <p>
 * 集中管理动态表操作中使用的配置键、Map 键名、默认前缀以及分页参数等常量，
 * 避免在业务代码中散落硬编码字符串。
 * </p>
 *
 * @author saperliu
 * @version 1.1.0
 * @see DialectConstant
 */
public final class Constants {

    private Constants() {
    }

    /**
     * 数据源配置项 Key
     */
    public static final String DATABASE_TYPE_KEY = "mybatis.database.type";
    /**
     * 模型包路径配置项 Key
     */
    public static final String MODEL_PACK_KEY = "mybatis.model.pack";
    /**
     * 自动建表模式配置项 Key
     */
    public static final String TABLE_AUTO_KEY = "mybatis.table.auto";

    /**
     * 数据源配置项 Value 占位符
     */
    public static final String DATABASE_TYPE_KEY_VALUE = "${mybatis.database.type:NULL}";
    /**
     * 模型包路径配置项 Value 占位符
     */
    public static final String MODEL_PACK_KEY_VALUE = "${mybatis.model.pack:NULL}";
    /**
     * 自动建表模式配置项 Value 占位符
     */
    public static final String TABLE_AUTO_KEY_VALUE = "${mybatis.table.auto:NULL}";

    /**
     * 配置缺省 NULL 标识
     */
    public static final String NULL = "NULL";

    /**
     * 新建表 Map Key
     */
    public static final String NEW_TABLE_MAP = "newTableMap";
    /**
     * 修改表结构 Map Key
     */
    public static final String MODIFY_TABLE_MAP = "modifyTableMap";
    /**
     * 新增字段 Map Key
     */
    public static final String ADD_TABLE_MAP = "addTableMap";
    /**
     * 删除字段 Map Key
     */
    public static final String REMOVE_TABLE_MAP = "removeTableMap";
    /**
     * 删除主键 Map Key
     */
    public static final String DROPKEY_TABLE_MAP = "dropKeyTableMap";
    /**
     * 删除索引与唯一约束 Map Key
     */
    public static final String DROPINDEXANDUNIQUE_TABLE_MAP = "dropIndexAndUniqueTableMap";
    /**
     * 新增索引 Map Key
     */
    public static final String ADDINDEX_TABLE_MAP = "addIndexTableMap";
    /**
     * 新增唯一约束 Map Key
     */
    public static final String ADDUNIQUE_TABLE_MAP = "addUniqueTableMap";
    /**
     * 修改字段名 Map Key
     */
    public static final String MODIFY_FIELD_NAME = "modifyFieldName";

    /**
     * 默认索引前缀
     */
    public static final String IDX = DialectConstant.INDEX_PREFIX;
    /**
     * 默认唯一约束前缀
     */
    public static final String UNI = DialectConstant.UNIQUE_PREFIX;

    /**
     * 主键字段名
     */
    public static final String KEY_FIELD = "id";

    /**
     * 分页参数名：起始行偏移（由 pageNum 计算）
     */
    public static final String START_ROW = "start";
    /**
     * 分页参数名：每页大小
     */
    public static final String PAGE_SIZE = "pageSize";
    /**
     * 分页参数名：当前页码
     */
    public static final String PAGE_NUM = "pageNum";
    /**
     * 分页参数名：排序字段
     */
    public static final String ORDER_BY = "orderBys";

    /**
     * keyFieldMap 参数名
     */
    public static final String KEY_FIELD_MAP = "keyFieldMap";

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE_NUM = 1;
    /**
     * 默认每页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 动态表数据写入前需剔除的系统字段（表单元数据字段，非业务列），
     * key 为下划线命名
     */
    public static final Set<String> SYSTEM_FIELDS = Set.of(
            "table_name", "meta_data", "is_workflow", "workflow_code",
            "app_id", "key_field_map", "organization_id");
}