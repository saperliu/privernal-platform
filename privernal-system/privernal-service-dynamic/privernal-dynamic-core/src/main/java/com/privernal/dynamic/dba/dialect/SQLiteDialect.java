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

import com.privernal.dynamic.dba.constants.DialectConstant;
import com.privernal.dynamic.dba.type.SQLiteTypeMapper;
import com.privernal.dynamic.dba.vo.CreateTableVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SQLite 数据库方言实现
 * <p>
 * 基于 SQLite 3.44+ 语法实现 SQL 构建：
 * <ul>
 *   <li>无 ENGINE/CHARSET 表选项、无列内 COMMENT</li>
 *   <li>自增列必须内联 {@code INTEGER PRIMARY KEY AUTOINCREMENT}</li>
 *   <li>元数据走 {@code sqlite_master} / {@code pragma_*} 表值函数，
 *       输出列名对齐 information_schema 口径供消费方复用</li>
 *   <li>不支持 {@code ALTER TABLE ... MODIFY}（SQLite 列类型不具约束力，
 *       类型变更走 {@link #supportsModifyColumn()} 能力开关被调用方跳过）</li>
 * </ul>
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 */
@Component
public class SQLiteDialect extends AbstractDatabaseDialect {

    /** 方言名称 */
    public static final String DIALECT_NAME = "sqlite";

    @Autowired
    public SQLiteDialect(SQLiteTypeMapper typeMapper) {
        super(typeMapper);
        log.debug("SQLiteDialect initialized");
    }

    /**
     * SPI 无参构造函数（ServiceLoader 发现方言时使用，自建无状态类型映射器）
     */
    public SQLiteDialect() {
        super(new SQLiteTypeMapper());
        log.debug("SQLiteDialect initialized via SPI");
    }

    @Override
    public String getName() {
        return DIALECT_NAME;
    }

    /**
     * SQLite 的自增主键必须内联在列定义上（表级 PRIMARY KEY 约束 + AUTOINCREMENT 非法）。
     */
    @Override
    public String buildColumnDefinition(CreateTableVo vo) {
        if (vo.isFieldIsAutoIncrement()) {
            return vo.getFieldName() + " INTEGER PRIMARY KEY AUTOINCREMENT";
        }
        return super.buildColumnDefinition(vo);
    }

    @Override
    protected String buildAutoIncrementClause() {
        return "PRIMARY KEY AUTOINCREMENT";
    }

    @Override
    protected String buildDefaultValueClause(CreateTableVo vo) {
        String defaultValue = vo.getFieldDefaultValue();
        String fieldType = vo.getFieldType();

        if ("INTEGER".equals(fieldType)) {
            if (defaultValue == null || !defaultValue.matches("-?\\d+(\\.\\d+)?")) {
                throw new IllegalArgumentException("数值型字段的默认值必须是数字：" + defaultValue);
            }
            return "DEFAULT " + defaultValue;
        }

        return "DEFAULT '" + escapeDdlLiteral(defaultValue) + "'";
    }

    /**
     * SQLite 字符串字面量只认 '' 转义（反斜杠是普通字符，照搬 MySQL 的双写会污染值）
     */
    private static String escapeDdlLiteral(String value) {
        return (value == null ? "" : value).replace("'", "''");
    }

    @Override
    protected String buildCommentClause(String comment) {
        // SQLite 无列内注释语法
        return "";
    }

    @Override
    protected String buildTableOptions() {
        return "";
    }

    @Override
    protected String getModifyKeyword() {
        throw new UnsupportedOperationException("SQLite 不支持 ALTER TABLE ... MODIFY，调用前请检查 supportsModifyColumn()");
    }

    @Override
    public String buildAlterTableModifyColumn(String tableName, CreateTableVo column, String oldName) {
        throw new UnsupportedOperationException("SQLite 不支持列修改（类型无约束力，无需 MODIFY）；如需变更请重建表");
    }

    @Override
    public String buildTableExistsSql(String tableName) {
        return "SELECT COUNT(1) FROM sqlite_master WHERE type = 'table' AND name = '" + tableName + "'";
    }

    /**
     * 对齐 information_schema.columns 消费口径：消费方只读 column_name（增量建表 diff）。
     */
    @Override
    public String buildGetTableColumnsSql(String tableName) {
        return "SELECT name AS column_name, upper(type) AS data_type,"
                + " CASE WHEN \"notnull\" = 1 THEN 'NO' ELSE 'YES' END AS is_nullable,"
                + " dflt_value AS column_default,"
                + " CASE WHEN pk = 1 THEN 'PRI' ELSE '' END AS column_key,"
                + " '' AS extra"
                + " FROM pragma_table_info('" + tableName + "')";
    }

    @Override
    public String buildGetTableIndexesSql(String tableName) {
        return "SELECT name AS index_name FROM pragma_index_list('" + tableName + "')"
                + " WHERE lower(name) != 'primary' AND name NOT LIKE 'sqlite_autoindex_%'";
    }

    @Override
    public String buildDropIndexSql(String tableName, String indexName) {
        return "DROP INDEX IF EXISTS " + indexName;
    }

    @Override
    public String buildAlterTableRenameColumn(String tableName, String oldName, String newName) {
        return "ALTER TABLE " + tableName + " RENAME COLUMN " + oldName + " TO " + newName;
    }

    @Override
    public String buildDropPrimaryKeySql(String tableName, CreateTableVo column) {
        throw new UnsupportedOperationException("SQLite 不支持 DROP PRIMARY KEY，调用前请检查 supportsModifyColumn()");
    }

    @Override
    public String buildPaginationSql(String baseSql, int offset, int pageSize) {
        return baseSql + " LIMIT " + pageSize + " OFFSET " + offset;
    }

    /**
     * SQLite 优化器无超时提示，超时交由连接层/执行层兜底
     */
    @Override
    public String buildTimeoutHint(int timeoutMillis) {
        return "";
    }

    /**
     * 列值统一为 TEXT 时间串，strftime 直接可用；周粒度用 ISO 周（%G-%V，等价 MySQL %x-%v）
     */
    @Override
    public String buildTimeGroupSql(String column, String granularity) {
        String pattern;
        switch (granularity == null ? "DAY" : granularity.trim().toUpperCase()) {
            case "WEEK":
                pattern = "%G-%V";
                break;
            case "MONTH":
                pattern = "%Y-%m";
                break;
            case "YEAR":
                pattern = "%Y";
                break;
            case "HOUR":
                pattern = "%Y-%m-%d %H:00";
                break;
            case "MINUTE":
                pattern = "%Y-%m-%d %H:%M";
                break;
            case "DAY":
                pattern = "%Y-%m-%d";
                break;
            default:
                throw new IllegalArgumentException("不支持的时间粒度：" + granularity);
        }
        return "strftime('" + pattern + "', " + column + ")";
    }

    /**
     * SQLite 类型无约束力（TEXT/INTEGER 亲和性自动容纳），类型变更无需 DDL；
     * 能力开关置 false 后调用方整体跳过 MODIFY 差异。
     */
    @Override
    public boolean supportsModifyColumn() {
        return false;
    }

    /**
     * 多值字段影子列暂不支持（SUBSTRING_INDEX/JSON_UNQUOTE 等 MySQL 表达式在 SQLite
     * 需要重写；影子列是增量能力，返回 false 走优雅跳过路径，不影响建表与读写）
     */
    @Override
    public boolean supportsGeneratedShadowColumn() {
        return false;
    }

    @Override
    public int parseColumnLength(com.privernal.dynamic.dba.vo.MysqlTableInfo info) {
        // pragma_table_info 无长度信息，类型亲和性也不依赖长度
        return 0;
    }
}
