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

import cn.hutool.core.util.ReflectUtil;
import com.privernal.common.core.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 类路径扫描与字段读取
 *
 * @author saperliu
 */
public class ClassTools {

    private static final Logger log = LoggerFactory.getLogger(ClassTools.class);

    private ClassTools() {
    }

    /**
     * 扫描给定包（可用 {@code ,} 或 {@code ;} 分隔多个）下所有可实例化的类。
     *
     * <p>用 Spring 的 classpath scanner 而不是手工遍历 jar entry：后者只认 {@code file:} 与
     * {@code jar:} 两种协议，而可执行 jar 里应用自身的类位于 {@code BOOT-INF/classes/} 前缀下，
     * 手工遍历会匹配不到任何 entry 并<b>静默返回空集合</b>——表现就是自动建表一条都不建。
     * 接口与抽象类不在结果里（调用方要找的是带注解的实体类）。</p>
     *
     * @param packs 扫描的包，支持多个
     * @return 该包下的 class
     */
    public static Set<Class<?>> getClasses(String packs) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        if (StringUtils.isEmpty(packs)) {
            return classes;
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String pack : packs.split(",|;")) {
            String basePackage = pack.trim();
            if (basePackage.isEmpty()) {
                continue;
            }
            for (BeanDefinition candidate : scanner().findCandidateComponents(basePackage)) {
                String className = candidate.getBeanClassName();
                try {
                    // loadClass 而不是 Class.forName：避免为了拿注解就触发类的 static 初始化
                    classes.add(ClassUtils.forName(className, classLoader));
                } catch (ClassNotFoundException | LinkageError e) {
                    log.error("加载扫描到的类失败：{}", className, e);
                }
            }
        }
        return classes;
    }

    private static ClassPathScanningCandidateComponentProvider scanner() {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        // 不带任何注解条件，只要有独立可实例化的类就收进来
        provider.addIncludeFilter((reader, factory) -> true);
        return provider;
    }

    /**
     * 取出列表中每个对象的同名字段值
     *
     * @param objList   对象列表
     * @param fieldName 字段名
     * @return 字段值列表；缺字段的对象跳过并记错误日志，不中断整批
     */
    @SuppressWarnings("unchecked")
    public static <T, E> List<E> getPropertyValueList(List<T> objList, String fieldName) {
        List<E> list = new ArrayList<>();
        if (objList == null) {
            return list;
        }
        for (T object : objList) {
            if (object == null) {
                continue;
            }
            Field field = ReflectUtil.getField(object.getClass(), fieldName);
            if (field == null) {
                log.error("类 {} 没有字段 {}", object.getClass().getName(), fieldName);
                continue;
            }
            list.add((E) ReflectUtil.getFieldValue(object, field));
        }
        return list;
    }
}
