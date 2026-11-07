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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StringUtils 在用语义的行为基线。
 *
 * <p>约 880 处调用依赖这些判定。最要紧的一条是 isEmpty(String) 把纯空白算作空
 * （等价于 isBlank）—— 它与 commons-lang3、hutool 的 isEmpty 语义都不同，
 * 替换实现时最容易在这里漂移。</p>
 */
class StringUtilsSemanticsTest
{
    @Test
    @DisplayName("字符串判空：纯空白算空（与 commons-lang3 的 isEmpty 不同）")
    void blankCountsAsEmpty()
    {
        assertTrue(StringUtils.isEmpty((String) null));
        assertTrue(StringUtils.isEmpty(""));
        assertTrue(StringUtils.isEmpty(" "), "纯空格应算空");
        assertTrue(StringUtils.isEmpty("\t\n  "));
        assertFalse(StringUtils.isEmpty("a"));
        assertFalse(StringUtils.isEmpty(" a "));
    }

    @Test
    @DisplayName("isNotEmpty 是 isEmpty(String) 的严格取反")
    void isNotEmptyIsNegation()
    {
        for (String s : new String[] {null, "", " ", "x", " x "})
        {
            assertEquals(!StringUtils.isEmpty(s), StringUtils.isNotEmpty(s), "输入：" + s);
        }
    }

    @Test
    @DisplayName("集合/Map/数组：null 与空算空，含 null 元素不算空")
    void containers()
    {
        assertTrue(StringUtils.isEmpty((List<?>) null));
        assertTrue(StringUtils.isEmpty(Collections.emptyList()));
        assertFalse(StringUtils.isEmpty(Collections.singletonList(null)), "含一个 null 元素仍是非空");

        assertTrue(StringUtils.isEmpty((Map<?, ?>) null));
        assertTrue(StringUtils.isEmpty(new HashMap<>()));
        assertFalse(StringUtils.isEmpty(Collections.singletonMap("k", null)));

        assertTrue(StringUtils.isEmpty((Object[]) null));
        assertTrue(StringUtils.isEmpty(new Object[0]));
        assertFalse(StringUtils.isEmpty(new Object[] {null}));
    }

    @Test
    @DisplayName("重载解析：List 与其 toArray 走各自的判定")
    void overloadResolution()
    {
        List<String> list = new ArrayList<>();
        list.add("a");
        assertTrue(StringUtils.isNotEmpty(list));
        assertTrue(StringUtils.isNotEmpty(list.toArray()));
    }

    @Test
    @DisplayName("hasText 与 Spring 同语义：要求至少一个非空白字符")
    void hasTextMatchesSpring()
    {
        assertFalse(StringUtils.hasText(null));
        assertFalse(StringUtils.hasText(""));
        assertFalse(StringUtils.hasText("   "));
        assertFalse(StringUtils.hasText("\t\n"));
        assertTrue(StringUtils.hasText("a"));
        assertTrue(StringUtils.hasText(" a "));
    }

    @Test
    @DisplayName("substring(start)：null 返回空串、负索引从尾部算、越界夹边界")
    void substringOneArg()
    {
        assertEquals("", StringUtils.substring(null, 2));
        assertEquals("llo", StringUtils.substring("hello", 2));
        assertEquals("lo", StringUtils.substring("hello", -2));
        assertEquals("hello", StringUtils.substring("hello", -10), "负溢出后夹到 0");
        assertEquals("hello", StringUtils.substring("hello", 0));
        assertEquals("", StringUtils.substring("hello", 10), "start 超长返回空串");
        assertEquals("", StringUtils.substring("hello", 5), "start 等于长度返回空串");
        assertEquals("", StringUtils.substring("", 1));
    }

    @Test
    @DisplayName("substring(start,end)：负索引从尾部算、start>end 返回空串、超长夹到长度")
    void substringTwoArgs()
    {
        assertEquals("", StringUtils.substring(null, 1, 3));
        assertEquals("el", StringUtils.substring("hello", 1, 3));
        assertEquals("ell", StringUtils.substring("hello", 1, -1));
        assertEquals("ell", StringUtils.substring("hello", -4, -1), "start=-4→1，end=-1→4");
        assertEquals("hello", StringUtils.substring("hello", 0, 99), "end 超长被夹到长度");
        assertEquals("", StringUtils.substring("hello", 3, 1), "start>end 返回空串");
        assertEquals("", StringUtils.substring("hello", 4, 2));
    }

    @Test
    @DisplayName("驼峰转下划线：常见字段命名的转换结果")
    void underScoreCase()
    {
        assertNull(StringUtils.toUnderScoreCase(null));
        assertEquals("", StringUtils.toUnderScoreCase(""));
        assertEquals("user_id", StringUtils.toUnderScoreCase("userId"));
        assertEquals("created_time", StringUtils.toUnderScoreCase("createdTime"));
        assertEquals("a", StringUtils.toUnderScoreCase("A"));
    }

    @Test
    @DisplayName("全大写词整体转小写：没有大写转小写的边界，就不插分隔符")
    void underScoreCaseOnAllUpperCase()
    {
        // 既有实现只在「大写转小写的交界处」插下划线，全大写串不存在这种交界，
        // 因此 USERNAME 得到 username 而不是 u_s_e_r_n_a_m_e。锁住以防被智能拆分式改写。
        assertEquals("username", StringUtils.toUnderScoreCase("USERNAME"));
        String converted = StringUtils.toUnderScoreCase("HTTPSUrl");
        assertFalse(converted.chars().anyMatch(Character::isUpperCase), "结果应全小写：" + converted);
    }
}
