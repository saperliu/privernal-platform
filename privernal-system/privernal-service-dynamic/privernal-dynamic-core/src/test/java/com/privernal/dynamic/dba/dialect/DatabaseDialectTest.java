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

import com.privernal.dynamic.dba.type.MySQLTypeMapper;
import com.privernal.dynamic.dba.type.PostgreSQLTypeMapper;
import com.privernal.dynamic.dba.type.SQLServerTypeMapper;
import com.privernal.dynamic.dba.vo.CreateTableVo;
import com.privernal.dynamic.dba.vo.MysqlTableInfo;
import com.privernal.dynamic.datav.service.ComputeSpecs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据库方言 SQL 构建单元测试
 * <p>
 * 覆盖 MySQL、PostgreSQL、SQL Server 三种方言的 SQL 生成逻辑，
 * 确保每种方言生成的 SQL 语法正确且符合各数据库规范。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 */
@DisplayName("数据库方言 SQL 构建测试")
class DatabaseDialectTest {

    private MySQLDialect mysqlDialect;
    private PostgreSQLDialect postgresDialect;
    private SQLServerDialect sqlServerDialect;

    @BeforeEach
    void setUp() {
        mysqlDialect = new MySQLDialect(new MySQLTypeMapper());
        postgresDialect = new PostgreSQLDialect(new PostgreSQLTypeMapper());
        sqlServerDialect = new SQLServerDialect(new SQLServerTypeMapper());
    }

    /**
     * 创建一个基础的 varchar 字段 VO
     */
    private CreateTableVo buildVarcharColumn(String name, int length, boolean nullable) {
        CreateTableVo vo = new CreateTableVo();
        vo.setFieldName(name);
        vo.setFieldType("varchar");
        vo.setFieldLength(length);
        vo.setFieldTypeLength(1);
        vo.setFieldIsNull(nullable);
        return vo;
    }

    /**
     * 创建一个自增主键 int 字段 VO
     */
    private CreateTableVo buildAutoIncrementKeyColumn() {
        CreateTableVo vo = new CreateTableVo();
        vo.setFieldName("id");
        vo.setFieldType("int");
        vo.setFieldTypeLength(0);
        vo.setFieldIsNull(false);
        vo.setFieldIsKey(true);
        vo.setFieldIsAutoIncrement(true);
        return vo;
    }

    /**
     * 创建一个带默认值的字段 VO
     */
    private CreateTableVo buildColumnWithDefault(String name, String type, String defaultValue) {
        CreateTableVo vo = new CreateTableVo();
        vo.setFieldName(name);
        vo.setFieldType(type);
        vo.setFieldTypeLength(0);
        vo.setFieldIsNull(true);
        vo.setFieldDefaultValue(defaultValue);
        return vo;
    }

    // ==================== MySQL 方言测试 ====================

    @Nested
    @DisplayName("MySQL 方言测试")
    class MySQLDialectTest {

        @Test
        @DisplayName("方言名称应为 mysql")
        void getName_returnsMysql() {
            assertThat(mysqlDialect.getName()).isEqualTo("mysql");
        }

        @Test
        @DisplayName("构建建表 SQL 应包含 ENGINE=InnoDB 和 AUTO_INCREMENT")
        void buildCreateTableSql_containsMysqlSpecificSyntax() {
            List<CreateTableVo> columns = new ArrayList<>();
            columns.add(buildAutoIncrementKeyColumn());
            columns.add(buildVarcharColumn("name", 100, false));

            String sql = mysqlDialect.buildCreateTableSql("test_table", columns);

            assertThat(sql).contains("CREATE TABLE test_table");
            assertThat(sql).contains("AUTO_INCREMENT");
            assertThat(sql).contains("ENGINE=InnoDB");
            assertThat(sql).contains("utf8mb4");
            assertThat(sql).contains("PRIMARY KEY (id)");
        }

        @Test
        @DisplayName("构建 ALTER TABLE ADD COLUMN SQL")
        void buildAlterTableAddColumn() {
            CreateTableVo column = buildVarcharColumn("email", 200, true);
            column.setFieldComment("邮箱地址");

            String sql = mysqlDialect.buildAlterTableAddColumn("user_table", column);

            assertThat(sql).startsWith("ALTER TABLE user_table ADD");
            assertThat(sql).contains("varchar(200)");
            assertThat(sql).contains("COMMENT '邮箱地址'");
        }

        @Test
        @DisplayName("构建 DROP TABLE SQL")
        void buildDropTableSql() {
            String sql = mysqlDialect.buildDropTableSql("test_table");
            assertThat(sql).isEqualTo("DROP TABLE IF EXISTS test_table");
        }

        @Test
        @DisplayName("构建建索引 SQL")
        void buildCreateIndexSql() {
            List<String> cols = Arrays.asList("name", "status");
            String sql = mysqlDialect.buildCreateIndexSql("user_table", "idx_name_status", cols);

            assertThat(sql).isEqualTo("CREATE INDEX idx_name_status ON user_table (name, status)");
        }

        @Test
        @DisplayName("构建删除索引 SQL - MySQL 使用 ON table 语法")
        void buildDropIndexSql() {
            String sql = mysqlDialect.buildDropIndexSql("user_table", "idx_name");
            assertThat(sql).isEqualTo("DROP INDEX idx_name ON user_table");
        }

        @Test
        @DisplayName("构建表存在性检查 SQL - MySQL 包含 DATABASE()")
        void buildTableExistsSql() {
            String sql = mysqlDialect.buildTableExistsSql("user_table");
            assertThat(sql).contains("information_schema.tables");
            assertThat(sql).contains("table_name = 'user_table'");
            assertThat(sql).contains("DATABASE()");
        }

        @Test
        @DisplayName("构建分页 SQL - MySQL 使用 LIMIT offset,pageSize")
        void buildPaginationSql() {
            String sql = mysqlDialect.buildPaginationSql("SELECT * FROM t", 10, 20);
            assertThat(sql).isEqualTo("SELECT * FROM t LIMIT 10, 20");
        }

        @Test
        @DisplayName("构建 BIT 类型默认值 - true")
        void buildDefaultValue_bitTrue() {
            CreateTableVo vo = buildColumnWithDefault("flag", "bit", "true");
            String sql = mysqlDialect.buildColumnDefinition(vo);
            assertThat(sql).contains("DEFAULT 1");
        }

        @Test
        @DisplayName("构建 BIT 类型默认值 - false")
        void buildDefaultValue_bitFalse() {
            CreateTableVo vo = buildColumnWithDefault("flag", "bit", "false");
            String sql = mysqlDialect.buildColumnDefinition(vo);
            assertThat(sql).contains("DEFAULT 0");
        }

        @Test
        @DisplayName("构建 INT 类型默认值")
        void buildDefaultValue_int() {
            CreateTableVo vo = buildColumnWithDefault("count", "int", "10");
            String sql = mysqlDialect.buildColumnDefinition(vo);
            assertThat(sql).contains("DEFAULT 10");
        }

        @Test
        @DisplayName("构建 VARCHAR 类型默认值")
        void buildDefaultValue_varchar() {
            CreateTableVo vo = buildColumnWithDefault("status", "varchar", "active");
            vo.setFieldLength(50);
            vo.setFieldTypeLength(1);
            String sql = mysqlDialect.buildColumnDefinition(vo);
            assertThat(sql).contains("DEFAULT 'active'");
        }

        @Test
        @DisplayName("构建唯一约束 SQL")
        void buildCreateUniqueSql() {
            List<String> cols = Arrays.asList("email");
            String sql = mysqlDialect.buildCreateUniqueSql("user_table", "uni_email", cols);
            assertThat(sql).isEqualTo("CREATE UNIQUE INDEX uni_email ON user_table (email)");
        }

        @Test
        @DisplayName("构建修改列 SQL - MySQL 使用 MODIFY")
        void buildAlterTableModifyColumn() {
            CreateTableVo column = buildVarcharColumn("name", 150, false);
            String sql = mysqlDialect.buildAlterTableModifyColumn("user_table", column, "name");

            assertThat(sql).startsWith("ALTER TABLE user_table MODIFY");
            assertThat(sql).contains("varchar(150)");
        }

        @Test
        @DisplayName("构建删除列 SQL")
        void buildAlterTableDropColumn() {
            String sql = mysqlDialect.buildAlterTableDropColumn("user_table", "old_col");
            assertThat(sql).isEqualTo("ALTER TABLE user_table DROP COLUMN old_col");
        }

        @Test
        @DisplayName("构建列重命名 SQL")
        void buildAlterTableRenameColumn() {
            String sql = mysqlDialect.buildAlterTableRenameColumn("user_table", "old_name", "new_name");
            assertThat(sql).isEqualTo("ALTER TABLE user_table RENAME COLUMN old_name TO new_name");
        }

        @Test
        @DisplayName("构建列注释 SQL - MySQL 在列定义内联 COMMENT")
        void buildColumnComment() {
            String sql = mysqlDialect.buildColumnComment("user_table", "name", "姓名");
            assertThat(sql).isNull();
        }

        @Test
        @DisplayName("解析列长度 - 数值精度")
        void parseColumnLength_numeric() {
            MysqlTableInfo info = new MysqlTableInfo();
            info.setNumeric_precision("10");
            assertThat(mysqlDialect.parseColumnLength(info)).isEqualTo(10);
        }

        @Test
        @DisplayName("解析列长度 - 字符长度")
        void parseColumnLength_char() {
            MysqlTableInfo info = new MysqlTableInfo();
            info.setCharacter_maximum_length("255");
            assertThat(mysqlDialect.parseColumnLength(info)).isEqualTo(255);
        }

        @Test
        @DisplayName("判断自增列")
        void isAutoIncrement_true() {
            MysqlTableInfo info = new MysqlTableInfo();
            info.setExtra("auto_increment");
            assertThat(mysqlDialect.isAutoIncrement(info)).isTrue();
        }

        @Test
        @DisplayName("判断主键列")
        void isPrimaryKey_true() {
            MysqlTableInfo info = new MysqlTableInfo();
            info.setColumn_key("PRI");
            assertThat(mysqlDialect.isPrimaryKey(info)).isTrue();
        }
    }

    // ==================== PostgreSQL 方言测试 ====================

    @Nested
    @DisplayName("PostgreSQL 方言测试")
    class PostgreSQLDialectTest {

        @Test
        @DisplayName("方言名称应为 postgresql")
        void getName_returnsPostgresql() {
            assertThat(postgresDialect.getName()).isEqualTo("postgresql");
        }

        @Test
        @DisplayName("构建建表 SQL 应包含 GENERATED BY DEFAULT AS IDENTITY")
        void buildCreateTableSql_containsPostgresSpecificSyntax() {
            List<CreateTableVo> columns = new ArrayList<>();
            columns.add(buildAutoIncrementKeyColumn());
            columns.add(buildVarcharColumn("name", 100, false));

            String sql = postgresDialect.buildCreateTableSql("test_table", columns);

            assertThat(sql).contains("CREATE TABLE test_table");
            assertThat(sql).contains("GENERATED BY DEFAULT AS IDENTITY");
            assertThat(sql).doesNotContain("ENGINE=InnoDB");
            assertThat(sql).contains("PRIMARY KEY (id)");
        }

        @Test
        @DisplayName("构建 ALTER TABLE ADD COLUMN SQL")
        void buildAlterTableAddColumn() {
            CreateTableVo column = buildVarcharColumn("email", 200, true);
            String sql = postgresDialect.buildAlterTableAddColumn("user_table", column);

            assertThat(sql).startsWith("ALTER TABLE user_table ADD");
            assertThat(sql).contains("varchar(200)");
        }

        @Test
        @DisplayName("构建修改列 SQL - PostgreSQL 使用 ALTER COLUMN")
        void buildAlterTableModifyColumn() {
            CreateTableVo column = buildVarcharColumn("name", 150, false);
            column.setFieldDefaultValue("默认名称");
            String sql = postgresDialect.buildAlterTableModifyColumn("user_table", column, "name");

            assertThat(sql).startsWith("ALTER TABLE user_table ALTER COLUMN name");
            assertThat(sql).contains("TYPE varchar(150)");
            assertThat(sql).contains("SET NOT NULL");
        }

        @Test
        @DisplayName("构建 DROP TABLE SQL")
        void buildDropTableSql() {
            String sql = postgresDialect.buildDropTableSql("test_table");
            assertThat(sql).isEqualTo("DROP TABLE IF EXISTS test_table");
        }

        @Test
        @DisplayName("构建表存在性检查 SQL")
        void buildTableExistsSql() {
            String sql = postgresDialect.buildTableExistsSql("user_table");
            assertThat(sql).contains("information_schema.tables");
            assertThat(sql).contains("table_name = 'user_table'");
        }

        @Test
        @DisplayName("构建删除索引 SQL - PostgreSQL 使用 IF EXISTS")
        void buildDropIndexSql() {
            String sql = postgresDialect.buildDropIndexSql("user_table", "idx_name");
            assertThat(sql).isEqualTo("DROP INDEX IF EXISTS idx_name");
        }

        @Test
        @DisplayName("构建分页 SQL - PostgreSQL 使用 LIMIT ... OFFSET")
        void buildPaginationSql() {
            String sql = postgresDialect.buildPaginationSql("SELECT * FROM t", 10, 20);
            assertThat(sql).isEqualTo("SELECT * FROM t LIMIT 20 OFFSET 10");
        }

        @Test
        @DisplayName("构建列注释 SQL - PostgreSQL 使用 COMMENT ON COLUMN")
        void buildColumnComment() {
            String sql = postgresDialect.buildColumnComment("user_table", "name", "姓名");
            assertThat(sql).isEqualTo("COMMENT ON COLUMN user_table.name IS '姓名'");
        }

        @Test
        @DisplayName("构建列注释 SQL - 空注释返回 null")
        void buildColumnComment_nullForEmpty() {
            String sql = postgresDialect.buildColumnComment("user_table", "name", "");
            assertThat(sql).isNull();
        }

        @Test
        @DisplayName("构建列注释 SQL - null 注释返回 null")
        void buildColumnComment_nullForNull() {
            String sql = postgresDialect.buildColumnComment("user_table", "name", null);
            assertThat(sql).isNull();
        }

        @Test
        @DisplayName("构建布尔类型默认值")
        void buildDefaultValue_boolean() {
            CreateTableVo vo = buildColumnWithDefault("active", "boolean", "true");
            String sql = postgresDialect.buildColumnDefinition(vo);
            assertThat(sql).contains("DEFAULT true");
        }

        @Test
        @DisplayName("构建列重命名 SQL")
        void buildAlterTableRenameColumn() {
            String sql = postgresDialect.buildAlterTableRenameColumn("user_table", "old_name", "new_name");
            assertThat(sql).isEqualTo("ALTER TABLE user_table RENAME COLUMN old_name TO new_name");
        }

        @Test
        @DisplayName("判断自增列 - PostgreSQL 检测 identity/nextval")
        void isAutoIncrement_true() {
            MysqlTableInfo info = new MysqlTableInfo();
            info.setExtra("identity");
            assertThat(postgresDialect.isAutoIncrement(info)).isTrue();
        }

        @Test
        @DisplayName("判断自增列 - PostgreSQL 检测 nextval 默认值")
        void isAutoIncrement_nextval() {
            MysqlTableInfo info = new MysqlTableInfo();
            info.setColumn_default("nextval('seq')");
            assertThat(postgresDialect.isAutoIncrement(info)).isTrue();
        }

        @Test
        @DisplayName("解析默认值 - PostgreSQL 去除单引号")
        void parseColumnDefaultValue() {
            MysqlTableInfo info = new MysqlTableInfo();
            info.setColumn_default("'hello'");
            assertThat(postgresDialect.parseColumnDefaultValue(info)).isEqualTo("hello");
        }
    }

    // ==================== SQL Server 方言测试 ====================

    @Nested
    @DisplayName("SQL Server 方言测试")
    class SQLServerDialectTest {

        @Test
        @DisplayName("方言名称应为 sqlserver")
        void getName_returnsSqlServer() {
            assertThat(sqlServerDialect.getName()).isEqualTo("sqlserver");
        }

        @Test
        @DisplayName("构建建表 SQL 应包含 IDENTITY(1,1)")
        void buildCreateTableSql_containsSqlServerSpecificSyntax() {
            List<CreateTableVo> columns = new ArrayList<>();
            columns.add(buildAutoIncrementKeyColumn());
            columns.add(buildVarcharColumn("name", 100, false));

            String sql = sqlServerDialect.buildCreateTableSql("test_table", columns);

            assertThat(sql).contains("CREATE TABLE test_table");
            assertThat(sql).contains("IDENTITY(1,1)");
            assertThat(sql).doesNotContain("ENGINE=InnoDB");
            assertThat(sql).doesNotContain("GENERATED BY DEFAULT AS IDENTITY");
        }

        @Test
        @DisplayName("构建 ALTER TABLE ADD COLUMN SQL")
        void buildAlterTableAddColumn() {
            CreateTableVo column = buildVarcharColumn("email", 200, true);
            String sql = sqlServerDialect.buildAlterTableAddColumn("user_table", column);

            assertThat(sql).startsWith("ALTER TABLE user_table ADD");
            assertThat(sql).contains("varchar(200)");
        }

        @Test
        @DisplayName("构建修改列 SQL - SQL Server 使用 ALTER COLUMN")
        void buildAlterTableModifyColumn() {
            CreateTableVo column = buildVarcharColumn("name", 150, false);
            String sql = sqlServerDialect.buildAlterTableModifyColumn("user_table", column, "name");

            assertThat(sql).startsWith("ALTER TABLE user_table ALTER COLUMN name");
            assertThat(sql).contains("varchar(150)");
            assertThat(sql).contains("NOT NULL");
        }

        @Test
        @DisplayName("构建 DROP TABLE SQL")
        void buildDropTableSql() {
            String sql = sqlServerDialect.buildDropTableSql("test_table");
            assertThat(sql).isEqualTo("DROP TABLE IF EXISTS test_table");
        }

        @Test
        @DisplayName("构建表存在性检查 SQL")
        void buildTableExistsSql() {
            String sql = sqlServerDialect.buildTableExistsSql("user_table");
            assertThat(sql).contains("information_schema.tables");
            assertThat(sql).contains("table_name = 'user_table'");
        }

        @Test
        @DisplayName("构建删除索引 SQL")
        void buildDropIndexSql() {
            String sql = sqlServerDialect.buildDropIndexSql("user_table", "idx_name");
            assertThat(sql).isEqualTo("DROP INDEX idx_name ON user_table");
        }

        @Test
        @DisplayName("构建分页 SQL - SQL Server 使用 OFFSET ROWS FETCH NEXT")
        void buildPaginationSql() {
            String sql = sqlServerDialect.buildPaginationSql("SELECT * FROM t", 10, 20);
            assertThat(sql).isEqualTo("SELECT * FROM t OFFSET 10 ROWS FETCH NEXT 20 ROWS ONLY");
        }

        @Test
        @DisplayName("构建列注释 SQL - SQL Server 使用 sp_addextendedproperty")
        void buildColumnComment() {
            String sql = sqlServerDialect.buildColumnComment("user_table", "name", "姓名");
            assertThat(sql).contains("sp_addextendedproperty");
            assertThat(sql).contains("MS_Description");
            assertThat(sql).contains("'姓名'");
            assertThat(sql).contains("@level1name = N'user_table'");
            assertThat(sql).contains("@level2name = N'name'");
        }

        @Test
        @DisplayName("构建列重命名 SQL - SQL Server 使用 sp_rename")
        void buildAlterTableRenameColumn() {
            String sql = sqlServerDialect.buildAlterTableRenameColumn("user_table", "old_name", "new_name");
            assertThat(sql).isEqualTo("EXEC sp_rename 'user_table.old_name', 'new_name', 'COLUMN'");
        }

        @Test
        @DisplayName("判断自增列 - SQL Server 检测 identity")
        void isAutoIncrement_true() {
            MysqlTableInfo info = new MysqlTableInfo();
            info.setExtra("identity");
            assertThat(sqlServerDialect.isAutoIncrement(info)).isTrue();
        }

        @Test
        @DisplayName("解析默认值 - SQL Server 去除括号")
        void parseColumnDefaultValue() {
            MysqlTableInfo info = new MysqlTableInfo();
            info.setColumn_default("(getdate())");
            assertThat(sqlServerDialect.parseColumnDefaultValue(info)).isEqualTo("getdate()");
        }

        @Test
        @DisplayName("BIT 类型默认值 - true")
        void buildDefaultValue_bitTrue() {
            CreateTableVo vo = buildColumnWithDefault("flag", "bit", "true");
            String sql = sqlServerDialect.buildColumnDefinition(vo);
            assertThat(sql).contains("DEFAULT 1");
        }
    }

    // ==================== 跨方言一致性测试 ====================

    @Nested
    @DisplayName("跨方言一致性测试")
    class CrossDialectTest {

        @Test
        @DisplayName("三种方言的建表 SQL 均应包含 CREATE TABLE 和表名")
        void buildCreateTableSql_allDialectsContainTableName() {
            List<CreateTableVo> columns = new ArrayList<>();
            columns.add(buildAutoIncrementKeyColumn());

            String mysqlSql = mysqlDialect.buildCreateTableSql("test_table", columns);
            String pgSql = postgresDialect.buildCreateTableSql("test_table", columns);
            String ssSql = sqlServerDialect.buildCreateTableSql("test_table", columns);

            assertThat(mysqlSql).contains("CREATE TABLE test_table");
            assertThat(pgSql).contains("CREATE TABLE test_table");
            assertThat(ssSql).contains("CREATE TABLE test_table");
        }

        @Test
        @DisplayName("三种方言的 DROP TABLE SQL 应一致")
        void buildDropTableSql_allDialectsConsistent() {
            String mysqlSql = mysqlDialect.buildDropTableSql("t");
            String pgSql = postgresDialect.buildDropTableSql("t");
            String ssSql = sqlServerDialect.buildDropTableSql("t");

            assertThat(mysqlSql).isEqualTo("DROP TABLE IF EXISTS t");
            assertThat(pgSql).isEqualTo("DROP TABLE IF EXISTS t");
            assertThat(ssSql).isEqualTo("DROP TABLE IF EXISTS t");
        }

        @Test
        @DisplayName("三种方言的建索引 SQL 格式一致")
        void buildCreateIndexSql_allDialectsConsistent() {
            List<String> cols = Arrays.asList("col1", "col2");

            String mysqlSql = mysqlDialect.buildCreateIndexSql("t", "idx", cols);
            String pgSql = postgresDialect.buildCreateIndexSql("t", "idx", cols);
            String ssSql = sqlServerDialect.buildCreateIndexSql("t", "idx", cols);

            assertThat(mysqlSql).isEqualTo("CREATE INDEX idx ON t (col1, col2)");
            assertThat(pgSql).isEqualTo("CREATE INDEX idx ON t (col1, col2)");
            assertThat(ssSql).isEqualTo("CREATE INDEX idx ON t (col1, col2)");
        }

        @Test
        @DisplayName("三种方言的唯一约束 SQL 格式一致")
        void buildCreateUniqueSql_allDialectsConsistent() {
            List<String> cols = Arrays.asList("col1");

            String mysqlSql = mysqlDialect.buildCreateUniqueSql("t", "uni", cols);
            String pgSql = postgresDialect.buildCreateUniqueSql("t", "uni", cols);
            String ssSql = sqlServerDialect.buildCreateUniqueSql("t", "uni", cols);

            assertThat(mysqlSql).isEqualTo("CREATE UNIQUE INDEX uni ON t (col1)");
            assertThat(pgSql).isEqualTo("CREATE UNIQUE INDEX uni ON t (col1)");
            assertThat(ssSql).isEqualTo("CREATE UNIQUE INDEX uni ON t (col1)");
        }

        @Test
        @DisplayName("三种方言的 ALTER TABLE DROP COLUMN 格式一致")
        void buildAlterTableDropColumn_allDialectsConsistent() {
            String mysqlSql = mysqlDialect.buildAlterTableDropColumn("t", "c");
            String pgSql = postgresDialect.buildAlterTableDropColumn("t", "c");
            String ssSql = sqlServerDialect.buildAlterTableDropColumn("t", "c");

            assertThat(mysqlSql).isEqualTo("ALTER TABLE t DROP COLUMN c");
            assertThat(pgSql).isEqualTo("ALTER TABLE t DROP COLUMN c");
            assertThat(ssSql).isEqualTo("ALTER TABLE t DROP COLUMN c");
        }

        @Test
        @DisplayName("三种方言的 COUNT SQL 格式一致")
        void buildCountSql_allDialectsConsistent() {
            String base = "SELECT * FROM t";

            String mysqlSql = mysqlDialect.buildCountSql(base);
            String pgSql = postgresDialect.buildCountSql(base);
            String ssSql = sqlServerDialect.buildCountSql(base);

            assertThat(mysqlSql).isEqualTo("SELECT COUNT(1) FROM (SELECT * FROM t) AS count_query");
            assertThat(pgSql).isEqualTo("SELECT COUNT(1) FROM (SELECT * FROM t) AS count_query");
            assertThat(ssSql).isEqualTo("SELECT COUNT(1) FROM (SELECT * FROM t) AS count_query");
        }

        @Test
        @DisplayName("三种方言的列信息查询 SQL 均基于 information_schema")
        void buildGetTableColumnsSql_allDialectsUseInformationSchema() {
            String mysqlSql = mysqlDialect.buildGetTableColumnsSql("t");
            String pgSql = postgresDialect.buildGetTableColumnsSql("t");
            String ssSql = sqlServerDialect.buildGetTableColumnsSql("t");

            assertThat(mysqlSql).contains("information_schema.columns");
            assertThat(pgSql).contains("information_schema.columns");
            assertThat(ssSql).contains("information_schema.columns");
        }
    }

    @Nested
    @DisplayName("报表时间分组 SQL")
    class TimeGroupSql {

        @Test
        @DisplayName("MySQL 各粒度用 DATE_FORMAT，HOUR/MINUTE 必须带日期部分")
        void mysqlGranularityPatterns() {
            assertThat(mysqlDialect.buildTimeGroupSql("created_at", "YEAR"))
                    .isEqualTo("DATE_FORMAT(created_at, '%Y')");
            assertThat(mysqlDialect.buildTimeGroupSql("created_at", "MONTH"))
                    .isEqualTo("DATE_FORMAT(created_at, '%Y-%m')");
            assertThat(mysqlDialect.buildTimeGroupSql("created_at", "DAY"))
                    .isEqualTo("DATE_FORMAT(created_at, '%Y-%m-%d')");
            // 只取 %H 会把每天的同一小时跨天合并，画出来的「小时趋势」其实是跨天累计
            assertThat(mysqlDialect.buildTimeGroupSql("created_at", "HOUR"))
                    .isEqualTo("DATE_FORMAT(created_at, '%Y-%m-%d %H:00')");
            assertThat(mysqlDialect.buildTimeGroupSql("created_at", "MINUTE"))
                    .isEqualTo("DATE_FORMAT(created_at, '%Y-%m-%d %H:%i')");
            assertThat(mysqlDialect.buildTimeGroupSql("created_at", "minute"))
                    .isEqualTo("DATE_FORMAT(created_at, '%Y-%m-%d %H:%i')");
        }

        @Test
        @DisplayName("PostgreSQL 与 SQL Server 的 HOUR/MINUTE 同样带日期")
        void otherDialectsCarryDateOnSubDayGrains() {
            assertThat(postgresDialect.buildTimeGroupSql("created_at", "HOUR"))
                    .isEqualTo("to_char(created_at, 'YYYY-MM-DD HH24')");
            assertThat(postgresDialect.buildTimeGroupSql("created_at", "MINUTE"))
                    .isEqualTo("to_char(created_at, 'YYYY-MM-DD HH24:MI')");
            assertThat(sqlServerDialect.buildTimeGroupSql("created_at", "HOUR"))
                    .isEqualTo("FORMAT(created_at, 'yyyy-MM-dd HH:00')");
            assertThat(sqlServerDialect.buildTimeGroupSql("created_at", "MINUTE"))
                    .isEqualTo("FORMAT(created_at, 'yyyy-MM-dd HH:mm')");
        }

        /**
         * 白名单与方言实现必须同步：以后往 ComputeSpecs.GRANULARITIES 加粒度却忘了改某个方言时，
         * 这里会立刻失败，而不是等到线上拿到一个被静默降级过的分组。
         */
        @Test
        @DisplayName("白名单里的每个粒度，三种方言都能生成 SQL")
        void everyWhitelistedGrainIsImplementedByAllDialects() {
            for (String grain : ComputeSpecs.GRANULARITIES) {
                assertThat(mysqlDialect.buildTimeGroupSql("c", grain)).as("mysql %s", grain).isNotBlank();
                assertThat(postgresDialect.buildTimeGroupSql("c", grain)).as("pg %s", grain).isNotBlank();
                assertThat(sqlServerDialect.buildTimeGroupSql("c", grain)).as("sqlserver %s", grain).isNotBlank();
            }
        }

        @Test
        @DisplayName("未知粒度抛异常，不再静默降级成按天分组")
        void unknownGrainFailsInsteadOfSilentlyFallingBackToDay() {
            assertThatThrownBy(() -> mysqlDialect.buildTimeGroupSql("c", "SECOND"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SECOND");
            assertThatThrownBy(() -> postgresDialect.buildTimeGroupSql("c", "SECOND"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sqlServerDialect.buildTimeGroupSql("c", "SECOND"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}