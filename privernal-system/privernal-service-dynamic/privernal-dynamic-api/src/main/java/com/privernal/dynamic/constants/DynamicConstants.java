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

package com.privernal.dynamic.constants;


/**
 * 动态表单相关的常量
 *
 * @author saperliu
 * @date 2023-02-20
 */
public interface DynamicConstants {

    /**
     * 默认值类型
     * 数字
     * 文本
     * 日期
     * 字典
     */
    String DEFAULT_VALUE_TYPE_NUMBER = "NUMBER";
    String DEFAULT_VALUE_TYPE_TEXT = "TEXT";
    String DEFAULT_VALUE_TYPE_DATE = "DATE";
    String DEFAULT_VALUE_TYPE_DICT = "DICT";


    String BACK_SCRIPT = "backScript";
    String BACK_SCRIPT_ATTR = "script";
    String BACK_SCRIPT_TRANSACTIONAL = "transactional";

    /**
     * 默认数据源标识（登录上下文取不到业务库名时回退使用）
     * 必须留空：@DS 解析到未注册的数据源名会直接抛 CannotFindDataSourceException，
     * 空值才表示走 spring.datasource.dynamic.primary 配置的主数据源。
     */
    String DEFAULT_DATABASE = "";

    /**
     * 动态表单提交数据的协议字段（非业务列，落库前需剔除）
     */
    String FIELD_TABLE_CODE = "tableCode";

    /**
     * 新增数据后是否按表单绑定自动发起流程，显式传 false 时只落数据（草稿）
     */
    String FIELD_START_FLOW = "startFlow";
}
