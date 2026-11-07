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

import com.privernal.dynamic.dba.type.SQLServerTypeMapper;
import com.privernal.dynamic.dba.vo.CreateTableVo;
import com.privernal.dynamic.dba.vo.MysqlTableInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SQL Server 数据库方言实现
 * <p>
 * 基于 SQL Server 2016+ 语法特性实现 SQL 构建，包含：
 * <ul>
 *   <li>{@code IDENTITY(1,1)} 自增列语法</li>
 *   <li>{@code ALTER COLUMN} 修改列语法</li>
 *   <li>{@code sp_addextendedproperty} 列注释语法</li>
 *   <li>{@code sp_rename} 重命名列语法</li>
 *   <li>{@code OFFSET ... FETCH NEXT} 分页语法</li>
 * </ul>
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 */
@Component
public class SQLServerDialect extends AbstractDatabaseDialect {

    /** 方言名称 */
    public static final String DIALECT_NAME = "sqlserver";

    /**
     * 构造函数，注入 SQL Server 类型映射器
     *
     * @param typeMapper SQL Server 类型映射器
     */
    @Autowired
    public SQLServerDialect(SQLServerTypeMapper typeMapper) {
        super(typeMapper);
        log.debug("SQLServerDialect initialized");
    }

    /**
     * SPI 无参构造函数（ServiceLoader 发现方言时使用，自建无状态类型映射器）
     */
    public SQLServerDialect() {
        super(new SQLServerTypeMapper());
        log.debug("SQLServerDialect initialized via SPI");
    }

    @Override
    public String getName() {
        return DIALECT_NAME;
    }

    @Override
    protected String buildAutoIncrementClause() {
        return "IDENTITY(1,1)";
    }

        /**
     * DDL 字面量转义（S16）：建表语句不支持绑定参数，COMMENT/DEFAULT 里的引号与反斜杠
     * 必须转义，否则模板作者输入即二阶 DDL 注入。
     */
    private static String escapeDdlLiteral(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("'", "''");
    }

    @Override
    protected String buildDefaultValueClause(CreateTableVo vo) {
        String defaultValue = vo.getFieldDefaultValue();
        String fieldType = vo.getFieldType();

        if ("bit".equals(fieldType)) {
            return buildBitDefault(defaultValue);
        }

        if ("int".equals(fieldType) || "bigint".equals(fieldType)
                || "decimal".equals(fieldType) || "float".equals(fieldType)) {
            // 数值型默认值不允许引号/表达式等任何杂质（DDL 无绑定参数，字面量即最终 SQL 的一部分）
            if (defaultValue == null || !defaultValue.matches("-?\\d+(\\.\\d+)?")) {
                throw new IllegalArgumentException("数值型字段的默认值必须是数字：" + defaultValue);
            }
            return "DEFAULT " + defaultValue;
        }

        return "DEFAULT '" + escapeDdlLiteral(defaultValue) + "'";
    }

    /**
     * 构建 BIT 类型默认值
     */
    private String buildBitDefault(String defaultValue) {
        if ("true".equals(defaultValue) || "1".equals(defaultValue)) {
            return "DEFAULT 1";
        }
        if ("false".equals(defaultValue) || "0".equals(defaultValue)) {
            return "DEFAULT 0";
        }
        return "DEFAULT '" + escapeDdlLiteral(defaultValue) + "'";
    }

    @Override
    protected String buildCommentClause(String comment) {
        return "";
    }

    @Override
    public String buildColumnComment(String tableName, String columnName, String comment) {
        if (comment == null || comment.isEmpty()) {
            return null;
        }
        log.debug("Building column comment for {}.{}: {}", tableName, columnName, comment);
        return "EXEC sp_addextendedproperty @name = N'MS_Description', "
                + "@value = N'" + comment + "', "
                + "@level0type = N'SCHEMA', @level0name = N'dbo', "
                + "@level1type = N'TABLE',  @level1name = N'" + tableName + "', "
                + "@level2type = N'COLUMN', @level2name = N'" + columnName + "'";
    }

    @Override
    protected String buildTableOptions() {
        return "";
    }

    @Override
    protected String getModifyKeyword() {
        return "ALTER COLUMN";
    }

    @Override
    public String buildAlterTableModifyColumn(String tableName, CreateTableVo column, String oldName) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(tableName);
        sb.append(" ALTER COLUMN ").append(oldName);
        sb.append(" ").append(buildTypeDefinition(column));

        if (!column.isFieldIsNull()) {
            sb.append(" NOT NULL");
        }

        if (column.getFieldDefaultValue() != null && !column.getFieldDefaultValue().isEmpty()) {
            sb.append(" DEFAULT ").append(resolveDefaultValue(column));
        }

        log.debug("Building ALTER TABLE modify column SQL: {}", sb);
        return sb.toString();
    }

    /**
     * 解析默认值（数值类型不加引号）
     */
    private String resolveDefaultValue(CreateTableVo vo) {
        String defaultValue = vo.getFieldDefaultValue();
        String fieldType = vo.getFieldType();

        if ("bit".equals(fieldType)) {
            return ("true".equals(defaultValue) || "1".equals(defaultValue)) ? "1" : "0";
        }

        if ("int".equals(fieldType) || "bigint".equals(fieldType)
                || "decimal".equals(fieldType) || "float".equals(fieldType)) {
            return defaultValue;
        }

        return "'" + defaultValue + "'";
    }

    @Override
    public String buildAlterTableRenameColumn(String tableName, String oldName, String newName) {
        log.debug("Building rename column SQL: {}.{} -> {}.{}", tableName, oldName, tableName, newName);
        return "EXEC sp_rename '" + tableName + "." + oldName + "', '" + newName + "', 'COLUMN'";
    }

    @Override
    public String buildTableExistsSql(String tableName) {
        return "SELECT COUNT(1) FROM information_schema.tables WHERE table_name = '" + tableName + "'";
    }

    @Override
    public String buildGetTableColumnsSql(String tableName) {
        return "SELECT * FROM information_schema.columns WHERE table_name = '" + tableName + "'";
    }

    @Override
    public String buildGetTableIndexesSql(String tableName) {
        return "SELECT index_name FROM information_schema.statistics WHERE table_name = '"
                + tableName + "' AND index_name IS NOT NULL AND index_name NOT LIKE 'PK%'";
    }

    @Override
    public String buildDropIndexSql(String tableName, String indexName) {
        return "DROP INDEX " + indexName + " ON " + tableName;
    }

    @Override
    public String buildPaginationSql(String baseSql, int offset, int pageSize) {
        return baseSql + " OFFSET " + offset + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
    }

    @Override
    public boolean isAutoIncrement(MysqlTableInfo info) {
        String extra = info.getExtra();
        return extra != null && extra.contains("identity");
    }

    @Override
    public boolean isPrimaryKey(MysqlTableInfo info) {
        String key = info.getColumn_key();
        return "PRI".equalsIgnoreCase(key) || key != null && key.contains("PRI");
    }

    @Override
    public int parseColumnLength(MysqlTableInfo info) {
        String charLength = info.getCharacter_maximum_length();
        if (charLength != null && !charLength.isEmpty()) {
            return Integer.parseInt(charLength);
        }
        String numericPrecision = info.getNumeric_precision();
        if (numericPrecision != null && !numericPrecision.isEmpty()) {
            return Integer.parseInt(numericPrecision);
        }
        return 0;
    }

    @Override
    public String parseColumnDefaultValue(MysqlTableInfo info) {
        String defaultValue = info.getColumn_default();
        if (defaultValue == null) {
            return null;
        }
        if (defaultValue.startsWith("(") && defaultValue.endsWith(")")) {
            return defaultValue.substring(1, defaultValue.length() - 1);
        }
        return defaultValue;
    }

    /** 报表时间分组：FORMAT 按粒度格式化，周粒度用 DATEPART(week) 拼 ISO 风格年-周 */
    @Override
    public String buildTimeGroupSql(String column, String granularity) {
        String gran = granularity == null ? "DAY" : granularity.trim().toUpperCase();
        switch (gran) {
            case "WEEK":
                return "CONCAT(FORMAT(" + column + ", 'yyyy'), '-W', RIGHT('0' + CAST(DATEPART(wk, "
                        + column + ") AS varchar(2)), 2))";
            case "MONTH":
                return "FORMAT(" + column + ", 'yyyy-MM')";
            case "YEAR":
                return "FORMAT(" + column + ", 'yyyy')";
            case "HOUR":
                return "FORMAT(" + column + ", 'yyyy-MM-dd HH:00')";
            case "MINUTE":
                return "FORMAT(" + column + ", 'yyyy-MM-dd HH:mm')";
            case "DAY":
                return "FORMAT(" + column + ", 'yyyy-MM-dd')";
            default:
                throw new IllegalArgumentException("不支持的时间粒度：" + granularity);
        }
    }

    /** SQL Server 无 LIMIT，用 OFFSET-FETCH（调用方须保证聚合 SQL 已带 ORDER BY） */
    @Override
    public String buildLimitClause(int limit) {
        return " OFFSET 0 ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }

    /** SQL Server 无优化器超时提示，超时由执行层兜底 */
    @Override
    public String buildTimeoutHint(int timeoutMillis) {
        return "";
    }
}