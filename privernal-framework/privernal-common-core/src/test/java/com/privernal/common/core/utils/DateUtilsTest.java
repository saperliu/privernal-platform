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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 日期工具的行为基线。
 *
 * <p>在用的只有三个方法：parseDate（把请求参数里的日期字符串转 Date，解析失败返回 null 而非抛错）、
 * parseDateToStr、datePath（文件按 年/月/日 分目录）。
 * 其中 parseDate 接受的格式集合决定「前端传来的日期串能不能被认出来」，所以逐个格式钉住。</p>
 */
class DateUtilsTest
{
    private static Date fixedDate()
    {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.OCTOBER, 13, 14, 30, 5);
        return c.getTime();
    }

    @ParameterizedTest(name = "[{0}]")
    @DisplayName("parseDate 支持声明的全部 12 种格式")
    @CsvSource(delimiter = '|', value = {
            "2026-10-13|2026-10-13 00:00:00",
            "2026-10-13 14:30:05|2026-10-13 14:30:05",
            "2026-10-13 14:30|2026-10-13 14:30:00",
            "2026/10/13|2026-10-13 00:00:00",
            "2026/10/13 14:30:05|2026-10-13 14:30:05",
            "2026/10/13 14:30|2026-10-13 14:30:00",
            "2026.10.13|2026-10-13 00:00:00",
            "2026.10.13 14:30:05|2026-10-13 14:30:05",
            "2026.10.13 14:30|2026-10-13 14:30:00",
    })
    void parseDateAcceptsDeclaredFormats(String input, String expected)
    {
        Date parsed = DateUtils.parseDate(input);
        assertNotNull(parsed, "未被识别的输入：" + input);
        assertEquals(expected, DateUtils.parseDateToStr("yyyy-MM-dd HH:mm:ss", parsed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10", "2026/10", "2026.10"})
    @DisplayName("只有年月的格式也能解析，落到当月 1 号")
    void parseDateAcceptsYearMonth(String input)
    {
        Date parsed = DateUtils.parseDate(input);
        assertNotNull(parsed, "未被识别的输入：" + input);
        assertEquals(input.replace('/', '-').replace('.', '-') + "-01 00:00:00",
                DateUtils.parseDateToStr("yyyy-MM-dd HH:mm:ss", parsed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "not-a-date", "2026-13-45", "13/10/2026"})
    @DisplayName("无法解析时返回 null，不抛异常（请求参数可能任意）")
    void parseDateReturnsNullOnUnparseable(String input)
    {
        assertNull(DateUtils.parseDate(input));
    }

    @Test
    @DisplayName("null 入参返回 null")
    void parseDateHandlesNull()
    {
        assertNull(DateUtils.parseDate(null));
    }

    @Test
    @DisplayName("parseDateToStr 按给定模式格式化")
    void parseDateToStrFormats()
    {
        assertEquals("2026-10-13 14:30:05", DateUtils.parseDateToStr("yyyy-MM-dd HH:mm:ss", fixedDate()));
        assertEquals("20261013", DateUtils.parseDateToStr("yyyyMMdd", fixedDate()));
        assertEquals("2026", DateUtils.parseDateToStr("yyyy", fixedDate()));
    }

    @Test
    @DisplayName("datePath 形如 yyyy/MM/dd，月日补零")
    void datePathShape()
    {
        String path = DateUtils.datePath();
        assertNotNull(path);
        assertEquals(true, path.matches("^\\d{4}/\\d{2}/\\d{2}$"), "实际：" + path);
        assertEquals(DateUtils.parseDateToStr("yyyy/MM/dd", new Date()), path);
    }
}
