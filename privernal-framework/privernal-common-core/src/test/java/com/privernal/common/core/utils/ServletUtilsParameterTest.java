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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求参数取值的语义基线。
 *
 * <p>这里锁定的不是风格而是「坏输入不炸」这件事：请求参数直接来自 URL，
 * 任何用 {@code getParameterToInt} 取数字参数的调用方（分页参数是典型场景）
 * 都依赖「转换失败返回 null 而非抛异常」——一旦把转换失败变成抛异常，
 * 前端一个脏参数就能打出 500。</p>
 */
class ServletUtilsParameterTest
{
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp()
    {
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("缺失参数：String 走默认值，Integer/Boolean 走 null")
    void missingParameterFallsBack()
    {
        assertNull(ServletUtils.getParameter("absent"));
        assertEquals("dft", ServletUtils.getParameter("absent", "dft"));
        assertNull(ServletUtils.getParameterToInt("absent"));
        assertEquals(7, ServletUtils.getParameterToInt("absent", 7));
        assertNull(ServletUtils.getParameterToBool("absent"));
        assertFalse(ServletUtils.getParameterToBool("absent", false));
    }

    @Test
    @DisplayName("空串是「有值」，不落到默认值")
    void emptyStringIsNotMissing()
    {
        request.addParameter("pageNum", "");
        assertEquals("", ServletUtils.getParameter("pageNum", "1"));
        assertNull(ServletUtils.getParameterToInt("pageNum"));
    }

    @Test
    @DisplayName("纯空白按空处理，与 isBlank 口径一致")
    void blankValueBehavesAsEmpty()
    {
        request.addParameter("pageSize", "   ");
        assertEquals(20, ServletUtils.getParameterToInt("pageSize", 20));
    }

    @Test
    @DisplayName("数值两侧空白容忍，非法值返回默认值而不是抛异常")
    void illegalNumberDegradesQuietly()
    {
        request.addParameter("pageNum", " 10 ");
        assertEquals(10, ServletUtils.getParameterToInt("pageNum"));
        request.addParameter("bad", "abc");
        assertNull(ServletUtils.getParameterToInt("bad"));
        assertEquals(3, ServletUtils.getParameterToInt("bad", 3));
        request.addParameter("decimal", "1.5");
        assertNull(ServletUtils.getParameterToInt("decimal"));
    }

    @Test
    @DisplayName("布尔：true/false/yes/ok/no/1/0 均可识别，大小写与空白容忍")
    void recognisedBooleanWords()
    {
        request.addParameter("a", "true");
        request.addParameter("b", " TRUE ");
        request.addParameter("c", "yes");
        request.addParameter("d", "OK");
        request.addParameter("e", "1");
        request.addParameter("f", "False");
        request.addParameter("g", "no");
        request.addParameter("h", "0");
        assertTrue(ServletUtils.getParameterToBool("a"));
        assertTrue(ServletUtils.getParameterToBool("b"));
        assertTrue(ServletUtils.getParameterToBool("c"));
        assertTrue(ServletUtils.getParameterToBool("d"));
        assertTrue(ServletUtils.getParameterToBool("e"));
        assertFalse(ServletUtils.getParameterToBool("f"));
        assertFalse(ServletUtils.getParameterToBool("g"));
        assertFalse(ServletUtils.getParameterToBool("h"));
    }

    @Test
    @DisplayName("无法识别的布尔值回落到默认值")
    void unknownBooleanFallsBack()
    {
        request.addParameter("flag", "随便写的");
        assertNull(ServletUtils.getParameterToBool("flag"));
        assertTrue(ServletUtils.getParameterToBool("flag", true));
    }

    @Test
    @DisplayName("没有请求上下文时不得抛 NPE")
    void outsideRequestContextDoesNotBlowUp()
    {
        RequestContextHolder.resetRequestAttributes();
        assertNull(ServletUtils.getParameter("pageNum"));
        assertEquals("1", ServletUtils.getParameter("pageNum", "1"));
        assertEquals(15, ServletUtils.getParameterToInt("pageNum", 15));
        assertTrue(ServletUtils.getParameterToBool("flag", true));
    }
}
