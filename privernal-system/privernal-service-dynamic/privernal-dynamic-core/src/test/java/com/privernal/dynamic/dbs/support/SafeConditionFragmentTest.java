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

package com.privernal.dynamic.dbs.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1 修复的安全基线。
 *
 * <p>用例必须覆盖<b>生产端 FormTransferTable 的真实输出形态</b>（前导空格、无反引号列名、
 * BETWEEN 小写 and），而不是只喂手写理想片段——R4 的教训就是单测全绿而链路不通。</p>
 */
class SafeConditionFragmentTest {

    @Test
    @DisplayName("生产端形态：前导空格 + 无反引号列名（IN/LIKE/=/BETWEEN）")
    void producerShapes() {
        Object[] parsed = SafeConditionFragment.parse("  class_name IN ('a班', 'b班')");
        assertEquals("`class_name` IN (#{condParams.p1}, #{condParams.p2})", parsed[0]);
        Map<?, ?> params = (Map<?, ?>) parsed[1];
        assertEquals("a班", params.get("p1"));
        assertEquals("b班", params.get("p2"));

        parsed = SafeConditionFragment.parse("  student_name LIKE '%张三%'");
        assertEquals("`student_name` LIKE #{condParams.p1}", parsed[0]);
        assertEquals("%张三%", ((Map<?, ?>) parsed[1]).get("p1"));

        parsed = SafeConditionFragment.parse("  student_name = '罗小黑'");
        assertEquals("`student_name` = #{condParams.p1}", parsed[0]);

        parsed = SafeConditionFragment.parse("  created_time BETWEEN '2026-01-01' and  '2026-12-31'");
        assertEquals("`created_time` BETWEEN #{condParams.p1} AND #{condParams.p2}", parsed[0]);

        parsed = SafeConditionFragment.parse("  age BETWEEN 3 and 7");
        Map<?, ?> ageParams = (Map<?, ?>) parsed[1];
        assertEquals("`age` BETWEEN #{condParams.p1} AND #{condParams.p2}", parsed[0]);
        assertEquals(3L, ageParams.get("p1"));
        assertEquals(7L, ageParams.get("p2"));
    }

    @Test
    @DisplayName("反引号形态与等值/比较符（API 层输入）")
    void backtickShapes() {
        Object[] parsed = SafeConditionFragment.parse("`name` = '张三'");
        assertEquals("`name` = #{condParams.p1}", parsed[0]);
        assertEquals("张三", ((Map<?, ?>) parsed[1]).get("p1"));

        parsed = SafeConditionFragment.parse("`name` LIKE '%ab%''s'");
        assertEquals("`name` LIKE #{condParams.p1}", parsed[0]);
        assertEquals("%ab%'s", ((Map<?, ?>) parsed[1]).get("p1"));

        parsed = SafeConditionFragment.parse("`del_time` IS NULL");
        assertEquals("`del_time` IS NULL", parsed[0]);
        parsed = SafeConditionFragment.parse("`del_time` IS NOT NULL");
        assertEquals("`del_time` IS NOT NULL", parsed[0]);

        parsed = SafeConditionFragment.parse("`status` NOT IN ('0')");
        assertEquals("`status` NOT IN (#{condParams.p1})", parsed[0]);
    }

    @Test
    @DisplayName("大写 AND 链")
    void andChain() {
        Object[] parsed = SafeConditionFragment.parse("`a` = 1 AND `b` <> 'x'");
        assertEquals("`a` = #{condParams.p1} AND `b` <> #{condParams.p2}", parsed[0]);
        assertEquals(2, ((Map<?, ?>) parsed[1]).size());
    }

    @Test
    @DisplayName("注入形态全部拒绝")
    void rejectsInjections() {
        String[] injections = {
                "1=1",
                "`id` = 1 OR '1'='1",
                "`id` = (SELECT 1)",
                "`id` = 1; DROP TABLE system_user_base",
                "' OR '1'='1",
                "`id` IN (SELECT id FROM system_user_base)",
                "id = 1 and sleep(1)",
                "`id` = 'x' AND 1=1",
                "`id` LIKE CONCAT('%', 'x')",
                "`id` = 'x'--",
                "`id` = 0x1234",
                "`id` = 'x' OR `id` = 'y'",
                "(`id` = 1)",
                "`id` = 1 UNION SELECT 1",
                "`id`; --",
                "name BETWEEN 'a' and 'b' and sleep(1)",
                "name = 'x' and `y` = 1",
                "id BETWEEN 1 and sleep(1)"};
        for (String injection : injections) {
            assertThrows(IllegalArgumentException.class, () -> SafeConditionFragment.parse(injection),
                    "必须拒绝：" + injection);
        }
    }

    @Test
    @DisplayName("空串与裸列名拒绝")
    void rejectsDegenerate() {
        assertThrows(IllegalArgumentException.class, () -> SafeConditionFragment.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> SafeConditionFragment.parse("name"));
        assertTrue(SafeConditionFragment.parse("`a` = 1")[0].toString().contains("`a`"));
    }
}
