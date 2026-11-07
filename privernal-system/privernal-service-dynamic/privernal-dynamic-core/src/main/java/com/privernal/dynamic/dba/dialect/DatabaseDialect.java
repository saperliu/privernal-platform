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

package com.privernal.dynamic.dba.dialect;

import com.privernal.dynamic.dba.type.DatabaseTypeMapper;
import com.privernal.dynamic.dba.vo.CreateTableVo;
import com.privernal.dynamic.dba.vo.MysqlTableInfo;

import java.util.List;

/**
 * 数据库方言接口
 * <p>
 * 定义跨数据库的 SQL 构建方法，每种数据库实现该接口以提供特定的 SQL 语法。
 * 支持通过 Java SPI 机制自动发现新方言实现。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 * @see AbstractDatabaseDialect
 * @see MySQLDialect
 * @see PostgreSQLDialect
 * @see SQLServerDialect
 */
public interface DatabaseDialect {

    /**
     * 获取方言名称（如 mysql, postgresql, sqlserver）
     *
     * @return 方言名称
     */
    String getName();

    /**
     * 获取数据库类型映射器
     *
     * @return 类型映射器实例
     */
    DatabaseTypeMapper getTypeMapper();

    /**
     * 构建列定义 SQL
     *
     * @param vo 列信息
     * @return 列定义 SQL 片段
     */
    String buildColumnDefinition(CreateTableVo vo);

    /**
     * 构建 CREATE TABLE SQL
     *
     * @param tableName 表名
     * @param columns   列定义列表
     * @return CREATE TABLE SQL
     */
    String buildCreateTableSql(String tableName, List<CreateTableVo> columns);

    /**
     * 构建 ALTER TABLE ADD COLUMN SQL
     *
     * @param tableName 表名
     * @param column    列信息
     * @return ADD COLUMN SQL
     */
    String buildAlterTableAddColumn(String tableName, CreateTableVo column);

    /**
     * 构建 ALTER TABLE MODIFY COLUMN SQL
     *
     * @param tableName 表名
     * @param column    新列信息
     * @param oldName   旧列名
     * @return MODIFY COLUMN SQL
     */
    String buildAlterTableModifyColumn(String tableName, CreateTableVo column, String oldName);

    /**
     * 构建 ALTER TABLE DROP COLUMN SQL
     *
     * @param tableName  表名
     * @param columnName 列名
     * @return DROP COLUMN SQL
     */
    String buildAlterTableDropColumn(String tableName, String columnName);

    /**
     * 构建 ALTER TABLE RENAME COLUMN SQL
     *
     * @param tableName 表名
     * @param oldName   旧列名
     * @param newName   新列名
     * @return RENAME COLUMN SQL
     */
    String buildAlterTableRenameColumn(String tableName, String oldName, String newName);

    /**
     * 构建 DROP TABLE SQL
     *
     * @param tableName 表名
     * @return DROP TABLE SQL
     */
    String buildDropTableSql(String tableName);

    /**
     * 构建 CREATE INDEX SQL
     *
     * @param tableName  表名
     * @param indexName  索引名
     * @param columns    索引列
     * @return CREATE INDEX SQL
     */
    String buildCreateIndexSql(String tableName, String indexName, List<String> columns);

    /**
     * 构建 DROP INDEX SQL
     *
     * @param tableName 表名
     * @param indexName 索引名
     * @return DROP INDEX SQL
     */
    String buildDropIndexSql(String tableName, String indexName);

    /**
     * 构建 CREATE UNIQUE INDEX SQL
     *
     * @param tableName  表名
     * @param uniqueName 唯一约束名
     * @param columns    约束列
     * @return CREATE UNIQUE INDEX SQL
     */
    String buildCreateUniqueSql(String tableName, String uniqueName, List<String> columns);

    /**
     * 构建检查表是否存在的 SQL
     *
     * @param tableName 表名
     * @return 检查表存在 SQL
     */
    String buildTableExistsSql(String tableName);

    /**
     * 构建获取表列信息的 SQL
     *
     * @param tableName 表名
     * @return 获取列信息 SQL
     */
    String buildGetTableColumnsSql(String tableName);

    /**
     * 构建获取表索引的 SQL
     *
     * @param tableName 表名
     * @return 获取索引 SQL
     */
    String buildGetTableIndexesSql(String tableName);

    /**
     * 构建删除主键的 SQL
     *
     * @param tableName 表名
     * @param column    列信息
     * @return 删除主键 SQL
     */
    String buildDropPrimaryKeySql(String tableName, CreateTableVo column);

    /**
     * 构建分页查询 SQL
     *
     * @param baseSql  基础查询 SQL
     * @param offset   偏移量
     * @param pageSize 每页大小
     * @return 分页 SQL
     */
    String buildPaginationSql(String baseSql, int offset, int pageSize);

    /**
     * 构建 COUNT 查询 SQL
     *
     * @param baseSql 基础查询 SQL
     * @return COUNT SQL
     */
    String buildCountSql(String baseSql);

    /**
     * 构建列注释 SQL
     *
     * @param tableName  表名
     * @param columnName 列名
     * @param comment    注释内容
     * @return 列注释 SQL，若不支持则返回 null
     */
    String buildColumnComment(String tableName, String columnName, String comment);

    /**
     * 解析列长度
     *
     * @param info 表列信息
     * @return 列长度
     */
    int parseColumnLength(MysqlTableInfo info);

    /**
     * 解析列默认值
     *
     * @param info 表列信息
     * @return 默认值
     */
    String parseColumnDefaultValue(MysqlTableInfo info);

    /**
     * 判断是否为自增列
     *
     * @param info 表列信息
     * @return 是否自增
     */
    boolean isAutoIncrement(MysqlTableInfo info);

    /**
     * 判断是否为主键
     *
     * @param info 表列信息
     * @return 是否主键
     */
    boolean isPrimaryKey(MysqlTableInfo info);

    /**
     * 时间维度按粒度生成分组表达式（报表聚合的 GROUP BY 用，禁止写死 DATE_FORMAT）。
     * 粒度白名单：DAY / WEEK / MONTH / YEAR。
     *
     * @param column      时间列名（调用前必须过标识符校验）
     * @param granularity 粒度
     * @return 方言化分组表达式
     */
    String buildTimeGroupSql(String column, String granularity);

    /**
     * 查询结果上限子句（报表聚合行数上限）。
     * MySQL / PostgreSQL 返回 LIMIT n；SQL Server 返回 OFFSET-FETCH（调用方须保证已有 ORDER BY）。
     *
     * @param limit 行数上限
     * @return 方言化 LIMIT 子句（含前导空格）
     */
    String buildLimitClause(int limit);

    /**
     * 查询超时提示（报表聚合超时控制）。MySQL 返回 MAX_EXECUTION_TIME 优化器提示，
     * 其余方言返回空串（超时由连接层/执行层兜底）。
     *
     * @param timeoutMillis 超时毫秒数
     * @return 拼在 SELECT 之后的提示串（含前导空格），无实现时返回空串
     */
    String buildTimeoutHint(int timeoutMillis);

    /**
     * 是否支持"生成列 + 索引"（多值字段影子列的前提）。
     * 默认 false：不支持的方言会整体跳过影子列，不影响建表。
     *
     * @return 是否支持生成列
     */
    default boolean supportsGeneratedShadowColumn() {
        return false;
    }

    /**
     * 是否支持 {@code ALTER TABLE ... MODIFY COLUMN}。
     * 不支持时调用方跳过类型差异的 MODIFY DDL（如 SQLite 类型无约束力，无需改列）。
     *
     * @return 是否支持列修改
     */
    default boolean supportsModifyColumn() {
        return true;
    }

    /**
     * 生成列是否必须为 STORED 才能建索引（PostgreSQL 需要，MySQL 的 VIRTUAL 即可）。
     *
     * @return true 表示生成列要落成 STORED
     */
    default boolean requiresStoredGeneration() {
        return false;
    }

    /**
     * 构造多值字段"首值影子列"的生成表达式。
     * <p>
     * 必须对<b>全部历史存储形态</b>容错（{@code {"v":[...]}} 信封、{@code [...]} 裸数组、
     * {@code a,b} 逗号串、裸值、非法文本），且<b>不能因单行脏值让整条 DDL 失败</b>——
     * 影子列是增量能力，脏值应退化为原值而不是报错。
     * </p>
     *
     * @param sourceColumn 业务列名（数据库列名形态）
     * @return 生成表达式；不支持时返回 null
     */
    default String buildShadowColumnExpression(String sourceColumn) {
        return null;
    }
}