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

package com.privernal.common.web.controller;

import com.privernal.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * order by 片段的白名单闸门。
 *
 * <p>{@code order by} 不能用占位符绑定，只能拼进语句，所以这一段是注入链路上唯一的机会。
 * 关键在于校验的必须是<b>拼接后的整体</b>：字段名和排序方向都是请求带来的输入，
 * 只过滤字段名、把方向原样拼进去，等于留了一个后门。</p>
 */
class OrderSqlValidationTest
{
    /** 括号与子查询都不在 SQL_PATTERN 白名单内 */
    private static final String INJECTION = "desc,(select 1 from system_user_base)";

    @Test
    @DisplayName("方向参数带注入时拒绝")
    void rejectsInjectionInDirection()
    {
        assertThrows(ServiceException.class, () -> BaseController.resolveOrderBy("create_time", INJECTION));
    }

    @Test
    @DisplayName("字段名带注入时拒绝")
    void rejectsInjectionInColumn()
    {
        assertThrows(ServiceException.class, () -> BaseController.resolveOrderBy("id; drop table x", "asc"));
    }

    @Test
    @DisplayName("正常字段与方向放行，原样拼接")
    void acceptsLegitimatePair()
    {
        assertEquals("create_time desc", BaseController.resolveOrderBy("create_time", "desc"));
        assertEquals("name asc", BaseController.resolveOrderBy("name", "asc"));
    }

    @Test
    @DisplayName("方向为空时只校验字段名")
    void validatesColumnWhenDirectionMissing()
    {
        assertEquals("create_time", BaseController.resolveOrderBy("create_time", null));
        assertEquals("create_time", BaseController.resolveOrderBy("create_time", ""));
        assertThrows(ServiceException.class, () -> BaseController.resolveOrderBy(INJECTION, null));
    }

    @Test
    @DisplayName("多字段与带表前缀的排序列仍在白名单内")
    void acceptsMultiColumnAndQualifiedNames()
    {
        assertEquals("name,age desc", BaseController.resolveOrderBy("name,age", "desc"));
        assertEquals("t.order_num asc", BaseController.resolveOrderBy("t.order_num", "asc"));
    }
}
