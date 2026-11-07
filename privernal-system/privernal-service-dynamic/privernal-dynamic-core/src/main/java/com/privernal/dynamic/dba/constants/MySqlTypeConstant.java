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

import com.privernal.dynamic.dba.annotation.LengthCount;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-30 10:24
 * @Description 用于配置Mysql数据库中类型，并且该类型需要设置几个长度
 * 这里配置多少个类型决定了，创建表能使用多少类型
 * 例如：varchar(1)
 * double(5,2)
 * datetime
 */
public class MySqlTypeConstant {

    @LengthCount(LengthCount = 0)
    public static final String INT = "int";

    @LengthCount
    public static final String VARCHAR = "varchar";

    @LengthCount(LengthCount = 0)
    public static final String TEXT = "text";

    @LengthCount(LengthCount = 0)
    public static final String MEDIUMTEXT = "mediumtext";

    @LengthCount(LengthCount = 0)
    public static final String LONGTEXT = "longtext";

    @LengthCount(LengthCount = 0)
    public static final String DATETIME = "datetime";

    @LengthCount(LengthCount = 2)
    public static final String DECIMAL = "decimal";

    @LengthCount(LengthCount = 2)
    public static final String DOUBLE = "double";

    @LengthCount
    public static final String CHAR = "char";

    @LengthCount
    public static final String BIGINT = "bigint";

    @LengthCount(LengthCount = 1)
    public static final String BIT = "bit";

    @LengthCount(LengthCount = 0)
    public static final String TIMESTAMP = "timestamp";

    @LengthCount(LengthCount = 0)
    public static final String DATE = "date";

    @LengthCount(LengthCount = 0)
    public static final String TIME = "time";

    @LengthCount(LengthCount = 0)
    public static final String FLOAT = "float";

    @LengthCount(LengthCount = 0)
    public static final String JSON = "json";
}
