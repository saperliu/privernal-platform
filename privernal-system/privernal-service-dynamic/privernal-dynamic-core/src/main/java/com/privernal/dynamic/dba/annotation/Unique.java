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
 * @Description 设置字段唯一约束
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Unique {

    /**
     * 唯一约束的名字，不设置默认为{uni_当前标记字段名@Column的name}
     *
     * @return
     */
    public String name() default "";

    /**
     * 唯一约束的字段名，不设置默认为当前标记字段名@Column的name
     * <p>可设置多个建立联合唯一{"login_mobile","login_name"}
     *
     * @return
     */
    public String[] value() default {};
}
