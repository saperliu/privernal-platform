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

package com.privernal.common.mybatis.mapper;

import java.lang.annotation.*;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2022-11-13 21:01
 * @Description 全局已经注入,在逻辑删除时会自动填写
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeleteFill {

    /**
     * 加了此注解后，在逻辑删除时会自动填写
     */
    public String value() default "1";

}
