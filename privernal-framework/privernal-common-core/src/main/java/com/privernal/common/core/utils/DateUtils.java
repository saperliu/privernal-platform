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

package com.privernal.common.core.utils;

import org.apache.commons.lang3.time.DateFormatUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 时间工具类。解析入口只保留一个 {@link #parseDate(Object)}：
 * 按常见中/英分隔格式逐个尝试，并要求字段合法（strict 模式）。
 *
 * @author saperliu
 */
public class DateUtils extends org.apache.commons.lang3.time.DateUtils
{
    /**
     * 依次尝试的日期/日期时间格式。含 - / . 三种分隔符与有无时间两类。
     */
    private static final String[] PARSE_PATTERNS = {
            "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM",
            "yyyy/MM/dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy/MM",
            "yyyy.MM.dd", "yyyy.MM.dd HH:mm:ss", "yyyy.MM.dd HH:mm", "yyyy.MM"};

    public static String parseDateToStr(final String format, final Date date)
    {
        return new SimpleDateFormat(format).format(date);
    }

    /**
     * 日期路径，即年/月/日，如 2018/08/08（上传目录分层用）。
     */
    public static String datePath()
    {
        return DateFormatUtils.format(new Date(), "yyyy/MM/dd");
    }

    /**
     * 日期型字符串转日期：解析失败（含字段非法）一律返回 null。
     *
     * <p>严格模式是刻意的：commons-lang3 默认宽松解析会把 2026-13-45 这类
     * 非法字段进位成 2027-02-14、把 13/10/2026 解成公元 19 年，
     * 造成表单数据被静默写坏。这里要求字段合法，否则按解析失败处理。</p>
     */
    public static Date parseDate(Object str)
    {
        if (str == null)
        {
            return null;
        }
        try
        {
            return parseDateStrictly(str.toString(), PARSE_PATTERNS);
        }
        catch (ParseException e)
        {
            return null;
        }
    }
}
