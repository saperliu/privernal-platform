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

package com.privernal.common.datascope.annotation;

import java.lang.annotation.*;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2022-02-13 21:01
 * @Description 数据权限过滤注解
 */
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PrivernalDataScope {

    /**
     * 当前主表的别名。查询的sql语句的主数据表的别名。
     */
    public String mainAlias() default "";

    /**
     * 当前主表的部门字段名。查询的sql语句的部门字段的别名。
     */
    public String deptField() default "department_id";

    /**
     * 当前的权限标识。授权时选择的菜单数据的权限标识，如：system:user:list 表示用户的列表，
     * 程序会根据此权限标识查询授权给此标识的数据权限是什么。
     */
    public String permission() default "";

    /**
     * 操作，单个PrivernalMapper上的注解. 如 list
     *  它与 permissionMain 配合，生成: system:user:list
     */
    public String permissionOperation() default "";

    /**
     * 当前主表的权限字段的前缀，具体操作根据方法再进行拼接
     */
    public String permissionMain() default "";

    /**
     * 动态表单的一级权限标识，动态拼接二级和三级标识
     */
    public String permissionOne() default "";

    /**
     * 忽略的表
     * @return
     */
    public boolean ignoreDepartment() default false;
}
