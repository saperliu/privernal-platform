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

package com.privernal.dynamic.dba.service;

import com.privernal.dynamic.dba.vo.CreateTableVo;
import com.privernal.dynamic.dbs.domain.DynamicForm;

import java.util.List;
import java.util.Map;

/**
 * 自动建表服务接口
 * <p>
 * 提供基于注解扫描和动态表单的自动建表、改表能力，
 * 支持 MySQL / PostgreSQL / SQL Server 等多种数据库方言。
 * </p>
 *
 * @author saperliu
 * @version 1.1.0
 * @see com.privernal.dynamic.dba.service.impl.AutoCreateTableServiceImpl
 * @see com.privernal.dynamic.dba.dialect.DatabaseDialect
 */
public interface AutoCreateTableService {

    /**
     * 扫描 model 包中带有 {@link com.privernal.dynamic.dba.annotation.Table} 注解的类，
     * 自动创建或更新数据库表结构。
     * <p>
     * 根据配置项 {@code privernal.dynamic.table.auto} 的值决定行为：
     * <ul>
     *   <li>{@code none} - 不做任何操作</li>
     *   <li>{@code update} - 对比 model 与数据库表结构，执行增量更新</li>
     *   <li>{@code create} - 删除原表后重新创建</li>
     * </ul>
     * </p>
     *
     * @deprecated 请使用 {@link #autoCreateTable()} 替代，方法名不再绑定特定数据库
     */
    @Deprecated
    void createMysqlTable();

    /**
     * 扫描 model 包自动创建/更新表结构（推荐使用）
     *
     * @see #createMysqlTable()
     */
    void autoCreateTable();

    /**
     * 将动态表单的 JSON 数据转换为数据库表
     *
     * @param tableName 表名称
     * @param data      表单数据（含字段定义、类型、长度等）
     * @param isCreate  是否为新建表（true=新建，false=修改）
     */
    void createTransferTable(String tableName, Map<String, Object> data, boolean isCreate);

    /**
     * 根据动态表单定义创建或更新数据库表
     *
     * @param dynamicForm 动态表单实体
     * @param isCreate    是否为新建表（true=新建，false=修改）
     */
    void createDynamicTable(DynamicForm dynamicForm, boolean isCreate);

    /**
     * 按字段定义列表创建或更新表结构（子表等程序化建表入口，不走表单 schema 解析）
     *
     * @param tableName 表名称
     * @param fields    字段定义列表
     * @param isCreate  是否为新建表（true=新建，false=按现有表结构增量追加缺失字段）
     */
    void createTableByFields(String tableName, List<CreateTableVo> fields, boolean isCreate);
}