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

package com.privernal.dynamic.dba.annotation;

import java.lang.annotation.*;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-21 14:12
 * @Description 建表的必备注解
 */
// 该注解用于方法声明
@Target(ElementType.FIELD)
// VM将在运行期也保留注释，因此可以通过反射机制读取注解的信息
@Retention(RetentionPolicy.RUNTIME)
// 将此注解包含在javadoc中
@Documented
// 允许子类继承父类中的注解
@Inherited
public @interface Column {

    /**
     * 字段名
     *
     * @return 字段名
     */
    String name();

    /**
     * 字段类型
     *
     * @return 字段类型
     */
    String type();

    /**
     * 字段长度，默认是255
     *
     * @return 字段长度，默认是255
     */
    int length() default 255;

    /**
     * 小数点长度，默认是0
     *
     * @return 小数点长度，默认是0
     */
    int decimalLength() default 0;

    /**
     * 是否为可以为null，true是可以，false是不可以，默认为true
     *
     * @return 是否为可以为null，true是可以，false是不可以，默认为true
     */
    boolean isNull() default true;

    /**
     * 是否是主键，默认false
     *
     * @return 是否是主键，默认false
     */
    boolean isKey() default false;

    /**
     * 是否自动递增，默认false 只有主键才能使用
     *
     * @return 是否自动递增，默认false 只有主键才能使用
     */
    boolean isAutoIncrement() default false;

    /**
     * 默认值，默认为null
     *
     * @return 默认值，默认为null
     */
    String defaultValue() default "NULL";

    /**
     * 数据表字段备注
     *
     * @return 默认值，默认为空
     */
    String comment() default "";

}
