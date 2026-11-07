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
 * @Description 针对数据库类型加注解，用来标记该类型需要设置几个长度 例如： datetime/不需要设置 ,varchar(1)/需要1个, double(5,2)/需要两个
 * <p>默认长度1，即1的时候不需要设置</p>
 */
// 该注解用于方法声明
@Target(ElementType.FIELD)
// VM将在运行期也保留注释，因此可以通过反射机制读取注解的信息
@Retention(RetentionPolicy.RUNTIME)
// 将此注解包含在javadoc中
@Documented
// 允许子类继承父类中的注解
@Inherited
public @interface LengthCount {

    /**
     * 默认是1，0表示不需要设置，1表示需要设置一个，2表示需要设置两个
     *
     * @return 默认是1，0表示不需要设置，1表示需要设置一个，2表示需要设置两个
     */
    public int LengthCount() default 1;

}
