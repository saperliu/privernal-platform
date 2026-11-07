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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SqlUtil 的行为基线测试。
 *
 * 这组断言锁的是「哪些 order by 片段被放行、哪些被拒」，
 * 因为它是拼接排序字段前的最后一道注入闸门，实现替换时不得改变这个集合。
 */
class SqlUtilTest
{
    @Test
    @DisplayName("空值与空串原样返回，不抛异常")
    void blankValuesPassThrough()
    {
        assertEquals(null, SqlUtil.escapeOrderBySql(null));
        assertEquals("", SqlUtil.escapeOrderBySql(""));
        assertEquals("   ", SqlUtil.escapeOrderBySql("   "));
    }

    @Test
    @DisplayName("字母数字下划线空格逗号小数点的组合被放行")
    void legalFragmentsAreAccepted()
    {
        assertTrue(SqlUtil.isValidOrderBySql("order_num"));
        assertTrue(SqlUtil.isValidOrderBySql("order_num desc"));
        assertTrue(SqlUtil.isValidOrderBySql("a asc,b desc"));
        assertTrue(SqlUtil.isValidOrderBySql("t1.create_time"));
        assertTrue(SqlUtil.isValidOrderBySql("A9"));
    }

    @Test
    @DisplayName("含分号、引号、括号、注释符、空白控制字符的片段被判为非法")
    void illegalFragmentsAreRejected()
    {
        assertFalse(SqlUtil.isValidOrderBySql("a;drop table x"));
        assertFalse(SqlUtil.isValidOrderBySql("a'"));
        assertFalse(SqlUtil.isValidOrderBySql("a\""));
        assertFalse(SqlUtil.isValidOrderBySql("a(b)"));
        assertFalse(SqlUtil.isValidOrderBySql("a--b"));
        assertFalse(SqlUtil.isValidOrderBySql(null));
        assertFalse(SqlUtil.isValidOrderBySql("a/*x"));
        assertFalse(SqlUtil.isValidOrderBySql("中文字段"));
    }

    @Test
    @DisplayName("非法片段抛 ServiceException")
    void escapeThrowsOnIllegalFragment()
    {
        assertThrows(ServiceException.class, () -> SqlUtil.escapeOrderBySql("a;select 1"));
    }

    @Test
    @DisplayName("合法片段原值返回，不做大小写或空格改写")
    void escapeKeepsValueUnchanged()
    {
        assertEquals("order_num desc", SqlUtil.escapeOrderBySql("order_num desc"));
        assertEquals("t1.create_time asc", SqlUtil.escapeOrderBySql("t1.create_time asc"));
    }
}
