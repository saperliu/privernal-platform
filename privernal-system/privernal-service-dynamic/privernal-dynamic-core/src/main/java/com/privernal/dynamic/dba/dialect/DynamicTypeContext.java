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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 动态类型上下文
 * <p>
 * 基于 ThreadLocal 的方言与类型映射器上下文持有者，
 * 用于在运行时获取当前数据库方言及其类型映射策略。
 * 线程安全的静态变量，在应用启动时通过 {@link DialectAutoConfiguration} 初始化。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 * @see DialectAutoConfiguration
 * @see DatabaseDialect
 * @see DatabaseTypeMapper
 */
public class DynamicTypeContext {

    private static final Logger log = LoggerFactory.getLogger(DynamicTypeContext.class);

    /**
     * 当前生效的类型映射器（线程可见的静态变量）
     */
    private static volatile DatabaseTypeMapper typeMapper;

    /**
     * 当前生效的数据库方言（线程可见的静态变量）
     */
    private static volatile DatabaseDialect dialect;

    /**
     * 设置当前使用的类型映射器
     *
     * @param mapper 类型映射器实例
     */
    public static void setTypeMapper(DatabaseTypeMapper mapper) {
        typeMapper = mapper;
        log.debug("DynamicTypeContext typeMapper set to: {}", mapper != null ? mapper.getClass().getSimpleName() : "null");
    }

    /**
     * 获取当前类型映射器
     *
     * @return 类型映射器实例
     */
    public static DatabaseTypeMapper getTypeMapper() {
        return typeMapper;
    }

    /**
     * 设置当前使用的数据库方言
     *
     * @param d 数据库方言实例
     */
    public static void setDialect(DatabaseDialect d) {
        dialect = d;
        log.debug("DynamicTypeContext dialect set to: {}", d != null ? d.getName() : "null");
    }

    /**
     * 获取当前数据库方言
     *
     * @return 数据库方言实例
     */
    public static DatabaseDialect getDialect() {
        return dialect;
    }
}