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

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.dba.constants.DialectConstant;
import com.privernal.dynamic.dba.type.DatabaseTypeMapper;
import com.privernal.dynamic.dba.vo.CreateTableVo;
import com.privernal.dynamic.dba.vo.MysqlTableInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 数据库方言抽象实现
 * <p>
 * 提供跨数据库通用的 SQL 构建逻辑，子类只需实现少量方言相关的差异方法即可。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 * @see DatabaseDialect
 * @see MySQLDialect
 * @see PostgreSQLDialect
 * @see SQLServerDialect
 */
public abstract class AbstractDatabaseDialect implements DatabaseDialect {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 当前方言绑定的类型映射器
     */
    protected final DatabaseTypeMapper typeMapper;

    protected AbstractDatabaseDialect(DatabaseTypeMapper typeMapper) {
        this.typeMapper = typeMapper;
    }

    @Override
    public DatabaseTypeMapper getTypeMapper() {
        return typeMapper;
    }

    @Override
    public String buildColumnDefinition(CreateTableVo vo) {
        if (StringUtils.isNotEmpty(vo.getGeneratedExpression())) {
            return buildGeneratedColumnDefinition(vo);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(vo.getFieldName()).append(" ");
        sb.append(buildTypeDefinition(vo));

        if (!vo.isFieldIsNull()) {
            sb.append(" NOT NULL");
        } else {
            sb.append(" NULL");
        }

        if (vo.isFieldIsAutoIncrement()) {
            sb.append(" ").append(buildAutoIncrementClause());
        }

        if (!vo.isFieldIsAutoIncrement() && vo.getFieldDefaultValue() != null
                && !vo.getFieldDefaultValue().isEmpty() && !"NULL".equals(vo.getFieldDefaultValue())) {
            sb.append(" ").append(buildDefaultValueClause(vo));
        }

        if (vo.getFieldComment() != null && !vo.getFieldComment().isEmpty()) {
            sb.append(" ").append(buildCommentClause(vo.getFieldComment()));
        }

        if (vo.isFieldIsKey()) {
            sb.append(", PRIMARY KEY (").append(vo.getFieldName()).append(")");
        }

        return sb.toString();
    }

    /**
     * 生成列（影子列）定义：{@code name type GENERATED ALWAYS AS (expr) VIRTUAL|STORED}。
     * <p>
     * 刻意不输出 NOT NULL / DEFAULT / COMMENT：
     * <ul>
     *   <li>DEFAULT 对生成列非法；</li>
     *   <li>NOT NULL 会让历史脏值行在 ALTER TABLE ADD 时直接失败，影子列必须可为空；</li>
     *   <li>COMMENT 是 MySQL 列内语法，PostgreSQL 需要独立的 COMMENT ON 语句，统一省略最安全。</li>
     * </ul>
     */
    protected String buildGeneratedColumnDefinition(CreateTableVo vo) {
        StringBuilder sb = new StringBuilder();
        sb.append(vo.getFieldName()).append(" ").append(buildTypeDefinition(vo));
        sb.append(" GENERATED ALWAYS AS (").append(vo.getGeneratedExpression()).append(")");
        String storage = vo.getGeneratedStorage();
        sb.append(" ").append(storage == null || storage.isEmpty() ? "VIRTUAL" : storage);
        return sb.toString();
    }

    /**
     * 构建字段类型定义（含长度/精度）
     */
    protected String buildTypeDefinition(CreateTableVo vo) {
        StringBuilder sb = new StringBuilder();
        sb.append(vo.getFieldType());
        int lengthCount = vo.getFieldTypeLength();

        if (lengthCount == 1) {
            sb.append("(").append(vo.getFieldLength()).append(")");
        } else if (lengthCount == 2) {
            sb.append("(").append(vo.getFieldLength()).append(",")
                    .append(vo.getFieldDecimalLength()).append(")");
        }

        return sb.toString();
    }

    /**
     * 构建自增列子句（各数据库方言实现）
     */
    protected abstract String buildAutoIncrementClause();

    /**
     * 构建默认值子句（各数据库方言实现）
     */
    protected abstract String buildDefaultValueClause(CreateTableVo vo);

    /**
     * 构建列注释子句（各数据库方言实现）
     */
    protected abstract String buildCommentClause(String comment);

    @Override
    public String buildCreateTableSql(String tableName, List<CreateTableVo> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(tableName).append(" (");

        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(buildColumnDefinition(columns.get(i)));
        }

        sb.append(")");
        sb.append(buildTableOptions());
        return sb.toString();
    }

    /**
     * 构建 CREATE TABLE 的表选项子句（引擎、字符集等），各数据库方言实现
     */
    protected abstract String buildTableOptions();

    @Override
    public String buildAlterTableAddColumn(String tableName, CreateTableVo column) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(tableName)
                .append(" ADD ").append(buildColumnDefinition(column));
        return sb.toString();
    }

    @Override
    public String buildAlterTableModifyColumn(String tableName, CreateTableVo column, String oldName) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(tableName)
                .append(" ").append(getModifyKeyword())
                .append(" ").append(buildColumnDefinition(column));
        return sb.toString();
    }

    /**
     * 获取 ALTER TABLE 修改列的关键字（MODIFY / ALTER COLUMN 等）
     */
    protected abstract String getModifyKeyword();

    @Override
    public String buildAlterTableDropColumn(String tableName, String columnName) {
        return "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
    }

    @Override
    public String buildAlterTableRenameColumn(String tableName, String oldName, String newName) {
        return "ALTER TABLE " + tableName + " RENAME COLUMN " + oldName + " TO " + newName;
    }

    @Override
    public String buildDropTableSql(String tableName) {
        return "DROP TABLE IF EXISTS " + tableName;
    }

    /**
     * MySQL 风格的默认实现：DATE_FORMAT 按粒度格式化；周粒度用 %x-%v（ISO 年-周）。
     * PostgreSQL / SQL Server 各自覆写。
     */
    @Override
    public String buildTimeGroupSql(String column, String granularity) {
        String pattern;
        switch (granularity == null ? "DAY" : granularity.trim().toUpperCase()) {
            case "WEEK":
                pattern = "%x-%v";
                break;
            case "MONTH":
                pattern = "%Y-%m";
                break;
            case "YEAR":
                pattern = "%Y";
                break;
            case "HOUR":
                // 必须带日期部分：只取 %H 会把每一天的 9 点合成一组，
                // 于是「小时趋势」图实际画的是跨天累计
                pattern = "%Y-%m-%d %H:00";
                break;
            case "MINUTE":
                pattern = "%Y-%m-%d %H:%i";
                break;
            case "DAY":
                pattern = "%Y-%m-%d";
                break;
            default:
                // 不降级成 DAY：静默按天分组会让「小时粒度」看起来正常出图，
                // 而数字其实是错的——这类错误没人会去核对
                throw new IllegalArgumentException("不支持的时间粒度：" + granularity);
        }
        return "DATE_FORMAT(" + column + ", '" + pattern + "')";
    }

    @Override
    public String buildLimitClause(int limit) {
        return " LIMIT " + limit;
    }

    @Override
    public String buildTimeoutHint(int timeoutMillis) {
        // MySQL 优化器提示：SELECT /*+ MAX_EXECUTION_TIME(n) */ ...
        return " /*+ MAX_EXECUTION_TIME(" + timeoutMillis + ") */";
    }

    @Override
    public String buildCreateIndexSql(String tableName, String indexName, List<String> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE INDEX ").append(indexName)
                .append(" ON ").append(tableName).append(" (");
        sb.append(String.join(", ", columns));
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String buildDropIndexSql(String tableName, String indexName) {
        return "DROP INDEX " + indexName + " ON " + tableName;
    }

    @Override
    public String buildCreateUniqueSql(String tableName, String uniqueName, List<String> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE UNIQUE INDEX ").append(uniqueName)
                .append(" ON ").append(tableName).append(" (");
        sb.append(String.join(", ", columns));
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String buildTableExistsSql(String tableName) {
        return "SELECT COUNT(1) FROM information_schema.tables WHERE table_name = '"
                + tableName + "'";
    }

    @Override
    public String buildGetTableColumnsSql(String tableName) {
        return "SELECT * FROM information_schema.columns WHERE table_name = '" + tableName + "'";
    }

    @Override
    public String buildGetTableIndexesSql(String tableName) {
        return "SELECT index_name FROM information_schema.statistics WHERE table_name = '"
                + tableName + "' AND lower(index_name) != 'primary'";
    }

    @Override
    public String buildDropPrimaryKeySql(String tableName, CreateTableVo column) {
        return "ALTER TABLE " + tableName + " " + getModifyKeyword()
                + " " + buildColumnDefinition(column) + ", DROP PRIMARY KEY";
    }

    @Override
    public String buildPaginationSql(String baseSql, int offset, int pageSize) {
        return baseSql + " LIMIT " + offset + ", " + pageSize;
    }

    @Override
    public String buildCountSql(String baseSql) {
        return "SELECT COUNT(1) FROM (" + baseSql + ") AS count_query";
    }

    @Override
    public String buildColumnComment(String tableName, String columnName, String comment) {
        return null;
    }

    @Override
    public int parseColumnLength(MysqlTableInfo info) {
        String numericPrecision = info.getNumeric_precision();
        if (numericPrecision != null && !numericPrecision.isEmpty()) {
            return Integer.parseInt(numericPrecision);
        }
        String charLength = info.getCharacter_maximum_length();
        if (charLength != null && !charLength.isEmpty()) {
            return Integer.parseInt(charLength);
        }
        return 0;
    }

    @Override
    public String parseColumnDefaultValue(MysqlTableInfo info) {
        return info.getColumn_default();
    }

    @Override
    public boolean isAutoIncrement(MysqlTableInfo info) {
        String extra = info.getExtra();
        return extra != null && extra.toLowerCase().contains("auto_increment");
    }

    @Override
    public boolean isPrimaryKey(MysqlTableInfo info) {
        String key = info.getColumn_key();
        return "PRI".equalsIgnoreCase(key);
    }
}