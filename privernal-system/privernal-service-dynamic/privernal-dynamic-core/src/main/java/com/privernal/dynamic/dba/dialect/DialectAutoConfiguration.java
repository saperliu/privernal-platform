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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库方言自动配置类
 * <p>
 * 根据配置项 {@code privernal.dynamic.database.type} 或 DataSource 的元数据
 * 自动检测并选择合适的 {@link DatabaseDialect} 实现。
 * 支持通过 Java SPI 机制自动发现新方言实现，无需修改本配置类即可扩展。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 */
@Configuration
public class DialectAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DialectAutoConfiguration.class);

    /**
     * 数据库产品名到方言名的映射表
     */
    private static final Map<String, String> PRODUCT_TO_DIALECT = new ConcurrentHashMap<>();

    static {
        PRODUCT_TO_DIALECT.put(DialectConstant.PRODUCT_MYSQL, DialectConstant.DIALECT_MYSQL);
        PRODUCT_TO_DIALECT.put(DialectConstant.PRODUCT_POSTGRESQL, DialectConstant.DIALECT_POSTGRESQL);
        PRODUCT_TO_DIALECT.put(DialectConstant.PRODUCT_SQLSERVER, DialectConstant.DIALECT_SQLSERVER);
        PRODUCT_TO_DIALECT.put(DialectConstant.PRODUCT_SQLITE, DialectConstant.DIALECT_SQLITE);
    }

    /**
     * 用户显式配置的数据库类型（优先级最高）
     */
    @Value("${" + DialectConstant.CONFIG_KEY_DATABASE_TYPE + ":}")
    private String configuredDatabaseType;

    @Autowired(required = false)
    private DataSource dataSource;

    /**
     * Spring 容器中已注册的方言 Bean
     */
    @Autowired(required = false)
    private List<DatabaseDialect> springDialects;

    /**
     * 通过 Java SPI 机制发现的方言实现（作为 Spring Bean 列表的补充）
     */
    private List<DatabaseDialect> spiDialects;

    @PostConstruct
    public void init() {
        this.spiDialects = loadDialectsViaSPI();
        log.info("SPI 机制发现方言数量：{}", spiDialects.size());
    }

    /**
     * 加载所有可用的 {@link DatabaseDialect} 实现
     * <p>优先使用 Spring 容器中的 Bean，若容器为空则回退到 SPI 机制</p>
     * <p>单个 SPI 实现实例化失败（如缺少 public 无参构造器）仅告警，不阻断启动</p>
     */
    private List<DatabaseDialect> loadDialectsViaSPI() {
        List<DatabaseDialect> result = new ArrayList<>();
        try {
            ServiceLoader.Provider<DatabaseDialect> provider;
            for (Iterator<ServiceLoader.Provider<DatabaseDialect>> iterator =
                 ServiceLoader.load(DatabaseDialect.class).stream().iterator(); iterator.hasNext(); ) {
                provider = iterator.next();
                try {
                    result.add(provider.get());
                } catch (ServiceConfigurationError e) {
                    log.warn("SPI 方言实现加载失败，已跳过：{}", provider.type().getName(), e);
                }
            }
        } catch (ServiceConfigurationError e) {
            log.warn("SPI 方言发现失败，仅使用 Spring 容器中的方言 Bean", e);
        }
        return result;
    }

    /**
     * 获取所有可用方言（Spring Bean + SPI）
     */
    private List<DatabaseDialect> getAllDialects() {
        List<DatabaseDialect> all = new ArrayList<>();
        if (springDialects != null) {
            all.addAll(springDialects);
        }
        if (spiDialects != null) {
            for (DatabaseDialect spiDialect : spiDialects) {
                boolean exists = all.stream().anyMatch(d -> d.getName().equalsIgnoreCase(spiDialect.getName()));
                if (!exists) {
                    all.add(spiDialect);
                }
            }
        }
        return all;
    }

    /**
     * 根据方言名查找对应实现
     */
    private Optional<DatabaseDialect> findDialectByName(String dialectName) {
        List<DatabaseDialect> all = getAllDialects();
        return all.stream().filter(d -> d.getName().equalsIgnoreCase(dialectName)).findFirst();
    }

    /**
     * 自动配置并返回当前使用的 {@link DatabaseDialect}
     */
    @Bean
    public DatabaseDialect databaseDialect() {
        String dialectName = resolveDialectName();

        log.info("Auto-detected database dialect: {}", dialectName);

        List<DatabaseDialect> all = getAllDialects();
        DatabaseDialect dialect = findDialectByName(dialectName)
                .orElseGet(() -> {
                    log.warn("No dialect found for: {}, falling back to {}", dialectName, DialectConstant.DEFAULT_DIALECT);
                    return findDialectByName(DialectConstant.DEFAULT_DIALECT)
                            .orElseThrow(() -> new IllegalStateException("No default dialect available"));
                });

        DynamicTypeContext.setDialect(dialect);
        DynamicTypeContext.setTypeMapper(dialect.getTypeMapper());
        log.info("DynamicTypeContext initialized with dialect: {}", dialect.getName());

        return dialect;
    }

    /**
     * 解析方言名：先读配置项，其次读数据源元数据，最后使用默认方言
     */
    private String resolveDialectName() {
        if (configuredDatabaseType != null && !configuredDatabaseType.isEmpty()) {
            log.debug("使用配置项指定的方言：{}", configuredDatabaseType);
            return configuredDatabaseType;
        }

        if (dataSource == null) {
            log.warn("No DataSource available, defaulting to {}", DialectConstant.DEFAULT_DIALECT);
            return DialectConstant.DEFAULT_DIALECT;
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();
            log.info("Detected database product: {}", productName);

            if (productName != null) {
                for (Map.Entry<String, String> entry : PRODUCT_TO_DIALECT.entrySet()) {
                    if (productName.contains(entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }

            log.warn("Unknown database product: {}, defaulting to {}", productName, DialectConstant.DEFAULT_DIALECT);
            return DialectConstant.DEFAULT_DIALECT;

        } catch (Exception e) {
            log.error("Failed to detect database type, defaulting to {}", DialectConstant.DEFAULT_DIALECT, e);
            return DialectConstant.DEFAULT_DIALECT;
        }
    }
}