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

package com.privernal.common.core.utils.sql;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;

import java.util.regex.Pattern;

/**
 * 排序字段白名单校验。
 *
 * order by 片段无法用参数占位符绑定，只能拼进 SQL，因此这里是注入链路的最后一道闸门：
 * 只放行「字母、数字、下划线、空格、逗号、小数点」，其余字符一律拒绝。
 *
 * @author saperliu
 */
public class SqlUtil
{
    /**
     * 允许的字符集合，保持公开以便调用方与文档核对同一条规则。
     */
    public static final String SQL_PATTERN = "[a-zA-Z0-9_\\ \\,\\.]+";

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile(SQL_PATTERN);

    private SqlUtil()
    {
    }

    /**
     * 校验 order by 片段是否落在白名单内。
     */
    public static boolean isValidOrderBySql(String value)
    {
        return value != null && ORDER_BY_PATTERN.matcher(value).matches();
    }

    /**
     * 校验并原样返回；不合法时抛业务异常，避免把可疑片段拼进 SQL。
     */
    public static String escapeOrderBySql(String value)
    {
        if (StringUtils.isNotEmpty(value) && !isValidOrderBySql(value))
        {
            throw new ServiceException("参数不符合规范，不能进行查询");
        }
        return value;
    }
}
