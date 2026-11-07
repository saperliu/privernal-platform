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

/**
 * 数据库方言常量定义
 * <p>
 * 集中管理方言名称、数据库产品名称以及连接池默认配置，便于跨库适配与扩展。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 */
public final class DialectConstant {

    private DialectConstant() {
    }

    /**
     * 方言名称
     */
    public static final String DIALECT_MYSQL = "mysql";
    public static final String DIALECT_POSTGRESQL = "postgresql";
    public static final String DIALECT_SQLSERVER = "sqlserver";
    public static final String DIALECT_SQLITE = "sqlite";

    /**
     * 数据库产品名称（来自 DatabaseMetaData#getDatabaseProductName）
     */
    public static final String PRODUCT_MYSQL = "MySQL";
    public static final String PRODUCT_POSTGRESQL = "PostgreSQL";
    public static final String PRODUCT_SQLSERVER = "Microsoft SQL Server";
    public static final String PRODUCT_SQLITE = "SQLite";

    /**
     * 默认方言（当无法识别或配置缺失时使用）
     */
    public static final String DEFAULT_DIALECT = DIALECT_MYSQL;

    /**
     * 方言配置项 Key
     */
    public static final String CONFIG_KEY_DATABASE_TYPE = "privernal.dynamic.database.type";

    /**
     * 默认索引前缀
     */
    public static final String INDEX_PREFIX = "idx_";
    /**
     * 默认唯一约束前缀
     */
    public static final String UNIQUE_PREFIX = "uni_";

    /**
     * 连接池默认配置 —— MySQL
     */
    public static final class MySQLPool {
        public static final int MIN_IDLE = 5;
        public static final int MAX_ACTIVE = 50;
        public static final long MAX_WAIT = 60_000L;
        public static final long TIME_BETWEEN_EVICTION = 60_000L;
        public static final long MIN_EVICTABLE_IDLE = 30_000L;
        public static final String VALIDATION_QUERY = "SELECT 1";

        private MySQLPool() {
        }
    }

    /**
     * 连接池默认配置 —— PostgreSQL
     */
    public static final class PostgreSQLPool {
        public static final int MIN_IDLE = 10;
        public static final int MAX_ACTIVE = 100;
        public static final long MAX_WAIT = 30_000L;
        public static final long TIME_BETWEEN_EVICTION = 30_000L;
        public static final long MIN_EVICTABLE_IDLE = 60_000L;
        public static final String VALIDATION_QUERY = "SELECT 1";

        private PostgreSQLPool() {
        }
    }

    /**
     * 连接池默认配置 —— SQL Server
     */
    public static final class SQLServerPool {
        public static final int MIN_IDLE = 5;
        public static final int MAX_ACTIVE = 100;
        public static final long MAX_WAIT = 30_000L;
        public static final long TIME_BETWEEN_EVICTION = 30_000L;
        public static final long MIN_EVICTABLE_IDLE = 60_000L;
        public static final String VALIDATION_QUERY = "SELECT 1";

        private SQLServerPool() {
        }
    }
}