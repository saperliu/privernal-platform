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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 消息模板格式化的行为基线。
 *
 * 唯一消费方是 ServiceException 的异常消息拼装，所以这里锁定的就是
 * 「业务异常里的 {} 会怎样被替换」，包括 \{} 转义这一条容易改错的路径。
 */
class StringFormatTest
{
    @Test
    @DisplayName("按顺序替换占位符")
    void formatReplacesInOrder()
    {
        assertEquals("this is a for b", StringUtils.format("this is {} for {}", "a", "b"));
    }

    @Test
    @DisplayName("\\{} 转义为字面量 {}，该参数不消耗")
    void escapedBraceIsLiteral()
    {
        assertEquals("this is {} for a", StringUtils.format("this is \\{} for {}", "a", "b"));
    }

    @Test
    @DisplayName("\\\\{} 时反斜杠本身输出，占位符仍然生效")
    void doubleEscapeKeepsBackslashAndFills()
    {
        assertEquals("this is \\a for b", StringUtils.format("this is \\\\{} for {}", "a", "b"));
    }

    @Test
    @DisplayName("参数多于占位符时，多余参数丢弃")
    void extraArgsIgnored()
    {
        assertEquals("a", StringUtils.format("{}", "a", "b"));
    }

    @Test
    @DisplayName("占位符少于参数时，模板尾部原样保留")
    void trailingTemplateKept()
    {
        assertEquals("a and {}", StringUtils.format("{} and {}", "a"));
    }

    @Test
    @DisplayName("无参数或空模板时原样返回")
    void noArgsReturnsTemplate()
    {
        assertEquals("plain text", StringUtils.format("plain text"));
        assertEquals("", StringUtils.format(""));
        assertNull(StringUtils.format(null));
    }

    @Test
    @DisplayName("参数为 null 与非字符串类型时的替换结果")
    void nullAndTypedArgs()
    {
        assertEquals("v=null", StringUtils.format("v={}", (Object) null));
        assertEquals("v=1", StringUtils.format("v={}", 1));
        assertEquals("v=true", StringUtils.format("v={}", true));
    }
}
