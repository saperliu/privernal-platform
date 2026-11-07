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

package com.privernal.dynamic.dba.utils;

import com.privernal.dynamic.dba.dialect.AbstractDatabaseDialect;
import com.privernal.dynamic.dba.dialect.DatabaseDialect;
import com.privernal.dynamic.dba.dialect.MySQLDialect;
import com.privernal.dynamic.dba.annotation.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动建表依赖的两件事：按包扫到实体类、按字段名取出列名列表。
 *
 * <p>扫描曾经用手工遍历 jar entry 实现，只认 {@code file:} 与 {@code jar:} 协议；
 * 可执行 jar 里应用自身的类带 {@code BOOT-INF/classes/} 前缀，于是扫到 0 个类，
 * 自动建表静默不生效而日志只写「扫描到 0 个 model 类」。这里锁住扫描确实能拿到类。</p>
 */
class ClassToolsTest
{
    @Test
    @DisplayName("能扫到带 @Table 的实体类，且注解可读")
    void findsAnnotatedEntities()
    {
        Set<Class<?>> classes = ClassTools.getClasses("com.privernal.dynamic.report.domain");
        assertFalse(classes.isEmpty(), "实体包不应扫为空");

        Set<String> names = classes.stream().map(Class::getName).collect(Collectors.toSet());
        assertTrue(names.contains("com.privernal.dynamic.report.domain.DynamicReport"), String.valueOf(names));

        // 自动建表就是靠这个注解筛出实体的
        long annotated = classes.stream().filter(c -> c.getAnnotation(Table.class) != null).count();
        assertEquals(4, annotated, "该包下 4 个实体都应带 @Table");
    }

    @Test
    @DisplayName("接口与抽象类不进结果，具体实现类进")
    void excludesInterfaceAndAbstractClass()
    {
        Set<Class<?>> classes = ClassTools.getClasses("com.privernal.dynamic.dba.dialect");
        Set<String> names = classes.stream().map(Class::getName).collect(Collectors.toSet());

        assertTrue(names.contains(MySQLDialect.class.getName()), String.valueOf(names));
        assertFalse(names.contains(DatabaseDialect.class.getName()), "接口不该被当成实体");
        assertFalse(names.contains(AbstractDatabaseDialect.class.getName()), "抽象类不该被当成实体");
    }

    @Test
    @DisplayName("多个包用逗号或分号分隔都能合并")
    void supportsMultiplePackages()
    {
        Set<Class<?>> comma = ClassTools.getClasses("com.privernal.dynamic.dba.dialect,com.privernal.dynamic.report.domain");
        Set<Class<?>> semicolon = ClassTools.getClasses("com.privernal.dynamic.dba.dialect;com.privernal.dynamic.report.domain");
        Set<Class<?>> single = ClassTools.getClasses("com.privernal.dynamic.dba.dialect");

        assertFalse(comma.isEmpty());
        assertEquals(semicolon.size(), comma.size());
        assertTrue(comma.size() > single.size(), "第二个包应带来额外结果");
    }

    @Test
    @DisplayName("空白输入与不存在的包返回空集合而不是异常")
    void toleratesBlankAndUnknownPackages()
    {
        assertTrue(ClassTools.getClasses(null).isEmpty());
        assertTrue(ClassTools.getClasses("").isEmpty());
        assertTrue(ClassTools.getClasses("  ").isEmpty());
        assertTrue(ClassTools.getClasses("com.privernal.no.such.package").isEmpty());
    }

    @Test
    @DisplayName("扫描结果的顺序稳定，重复调用一致")
    void scanIsRepeatable()
    {
        assertEquals(new ArrayList<>(ClassTools.getClasses("com.privernal.dynamic.dba.dialect")),
                new ArrayList<>(ClassTools.getClasses("com.privernal.dynamic.dba.dialect")));
    }

    @Test
    @DisplayName("按字段名批量取值")
    void readsFieldValueFromEachElement()
    {
        List<Column> columns = Arrays.asList(new Column("id"), new Column("user_name"));
        List<String> names = ClassTools.getPropertyValueList(columns, "name");
        assertEquals(Arrays.asList("id", "user_name"), names);
    }

    @Test
    @DisplayName("取不到字段时跳过该元素并继续，不静默丢掉后面的列")
    void missingFieldSkipsElementAndContinues()
    {
        List<Object> mixed = Arrays.asList(new Column("id"), "我不是 Column", new Column("age"));
        List<String> names = ClassTools.getPropertyValueList(mixed, "name");
        assertEquals(Arrays.asList("id", "age"), names, "缺字段的元素被跳过，其余照常取出");
    }

    @Test
    @DisplayName("null 列表与列表里的 null 元素都安全")
    void toleratesNullListAndNullElements()
    {
        assertTrue(ClassTools.getPropertyValueList(null, "name").isEmpty());
        List<Column> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(new Column("x"));
        assertEquals(List.of("x"), ClassTools.getPropertyValueList(withNull, "name"));
    }

    /**
     * 仅用于验证字段读取的样例类型
     */
    static class Column
    {
        private final String name;

        Column(String name)
        {
            this.name = name;
        }
    }
}
