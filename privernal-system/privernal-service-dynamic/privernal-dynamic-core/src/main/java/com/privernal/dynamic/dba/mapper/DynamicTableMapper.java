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

package com.privernal.dynamic.dba.mapper;

import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.dynamic.dba.vo.MysqlTableInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 动态表结构操作 Mapper
 * <p>
 * 本 Mapper 提供基于方言接口的动态 SQL 执行能力，所有 SQL 均由
 * {@link com.privernal.dynamic.dba.dialect.DatabaseDialect} 接口的实现生成，
 * 从而支持 MySQL / PostgreSQL / SQL Server 等多种数据库。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 * @see com.privernal.dynamic.dba.dialect.DatabaseDialect
 */
@Mapper
@IgnoreDataScope
public interface DynamicTableMapper {

    /**
     * 执行 DDL 语句（CREATE TABLE, ALTER TABLE, DROP INDEX 等）
     * <p>推荐使用此方法配合方言接口动态生成 SQL</p>
     *
     * @param sql 方言接口生成的 DDL SQL 语句
     */
    @IgnoreDataScope
    void executeDDL(@Param("sql") String sql);

    /**
     * 执行查询 SQL，返回单值（如 COUNT 查询）
     *
     * @param sql 查询 SQL 语句
     * @return 查询结果
     */
    @IgnoreDataScope
    int executeQueryForCount(@Param("sql") String sql);

    /**
     * 执行查询 SQL，返回表列信息列表
     *
     * @param sql 查询 SQL 语句
     * @return 列信息列表
     */
    @IgnoreDataScope
    List<MysqlTableInfo> executeQueryForColumns(@Param("sql") String sql);

    /**
     * 执行查询 SQL，返回索引名集合
     *
     * @param sql 查询 SQL 语句
     * @return 索引名集合
     */
    @IgnoreDataScope
    Set<String> executeQueryForIndexes(@Param("sql") String sql);
}