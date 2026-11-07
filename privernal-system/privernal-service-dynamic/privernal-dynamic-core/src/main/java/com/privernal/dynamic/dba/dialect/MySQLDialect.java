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
import com.privernal.dynamic.dba.vo.CreateTableVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * MySQL 数据库方言实现
 * <p>
 * 基于 MySQL 5.7+/8.0+ 语法特性实现 SQL 构建，包含：
 * <ul>
 *   <li>InnoDB 引擎及 utf8mb4 字符集配置</li>
 *   <li>AUTO_INCREMENT 自增列语法</li>
 *   <li>基于 {@code information_schema} 的表元数据查询</li>
 * </ul>
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 */
@Component
public class MySQLDialect extends AbstractDatabaseDialect {

    /** 方言名称 */
    public static final String DIALECT_NAME = "mysql";

    /**
     * 构造函数，注入 MySQL 类型映射器
     *
     * @param typeMapper MySQL 类型映射器
     */
    @Autowired
    public MySQLDialect(MySQLTypeMapper typeMapper) {
        super(typeMapper);
        log.debug("MySQLDialect initialized");
    }

    /**
     * SPI 无参构造函数（ServiceLoader 发现方言时使用，自建无状态类型映射器）
     */
    public MySQLDialect() {
        super(new MySQLTypeMapper());
        log.debug("MySQLDialect initialized via SPI");
    }

    @Override
    public String getName() {
        return DIALECT_NAME;
    }

    @Override
    protected String buildAutoIncrementClause() {
        return "AUTO_INCREMENT";
    }

    @Override
    protected String buildDefaultValueClause(CreateTableVo vo) {
        String defaultValue = vo.getFieldDefaultValue();
        String fieldType = vo.getFieldType();

        if ("bit".equals(fieldType)) {
            return buildBitDefault(defaultValue);
        }

        if ("int".equals(fieldType) || "decimal".equals(fieldType)) {
            // 数值型默认值不允许引号/表达式等任何杂质（DDL 无绑定参数，字面量即最终 SQL 的一部分）
            if (defaultValue == null || !defaultValue.matches("-?\\d+(\\.\\d+)?")) {
                throw new IllegalArgumentException("数值型字段的默认值必须是数字：" + defaultValue);
            }
            return "DEFAULT " + defaultValue;
        }

        return "DEFAULT '" + escapeDdlLiteral(defaultValue) + "'";
    }

    /**
     * DDL 字面量转义（S16）：建表语句不支持绑定参数，COMMENT/DEFAULT 里的引号与反斜杠
     * 必须转义，否则模板作者输入即二阶 DDL 注入。
     */
    private static String escapeDdlLiteral(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("'", "''");
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
        return "COMMENT '" + escapeDdlLiteral(comment) + "'";
    }

    @Override
    protected String buildTableOptions() {
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin";
    }

    @Override
    protected String getModifyKeyword() {
        return "MODIFY";
    }

    @Override
    public String buildTableExistsSql(String tableName) {
        return "SELECT COUNT(1) FROM information_schema.tables WHERE table_name = '"
                + tableName + "' AND table_schema = (SELECT DATABASE())";
    }

    @Override
    public String buildGetTableColumnsSql(String tableName) {
        return "SELECT * FROM information_schema.columns WHERE table_name = '"
                + tableName + "' AND table_schema = (SELECT DATABASE())";
    }

    @Override
    public String buildGetTableIndexesSql(String tableName) {
        return "SELECT index_name FROM information_schema.statistics WHERE table_name = '"
                + tableName + "' AND table_schema = (SELECT DATABASE()) AND LOWER(index_name) != 'primary'";
    }

    @Override
    public String buildDropIndexSql(String tableName, String indexName) {
        return "DROP INDEX " + indexName + " ON " + tableName;
    }

    @Override
    public String buildAlterTableRenameColumn(String tableName, String oldName, String newName) {
        return "ALTER TABLE " + tableName + " RENAME COLUMN " + oldName + " TO " + newName;
    }

    /**
     * MySQL 5.7+ 支持虚拟生成列 + 二级索引，影子列可用。
     */
    @Override
    public boolean supportsGeneratedShadowColumn() {
        return true;
    }

    /**
     * 虚拟生成列即可建索引，不需要 STORED（避免整表重建）。
     */
    @Override
    public boolean requiresStoredGeneration() {
        return false;
    }

    /**
     * 首值影子列表达式。必须对全部历史存储形态容错，且<b>不能因某行脏值让 DDL 或写入失败</b>：
     * <ul>
     *   <li>非合法 JSON（含 {@code a,b} 逗号串、裸值）→ 取第一段（逗号分隔语义）；</li>
     *   <li>{@code {"v":[...]}} 新信封 → {@code $.v[0]}；</li>
     *   <li>{@code [...]} 裸数组（历史多选/附件写法）→ {@code $[0]}；</li>
     *   <li>合法 JSON 标量（如数字、带引号字符串）→ 去引号原值。</li>
     * </ul>
     * {@code NULLIF(...,'')} 用于让"空字符串元素"退到下一个候选，而不是把影子列落成空串。
     */
    @Override
    public String buildShadowColumnExpression(String sourceColumn) {
        if (sourceColumn == null || sourceColumn.isEmpty()) {
            return null;
        }
        return "CASE WHEN " + sourceColumn + " IS NULL OR " + sourceColumn + " = '' THEN NULL"
                + " WHEN NOT JSON_VALID(" + sourceColumn + ") THEN SUBSTRING_INDEX(" + sourceColumn + ", ',', 1)"
                + " ELSE COALESCE("
                + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(" + sourceColumn + ", '$.v[0]')), ''), "
                + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(" + sourceColumn + ", '$[0]')), ''), "
                + "NULLIF(JSON_UNQUOTE(" + sourceColumn + "), ''))"
                + " END";
    }
}