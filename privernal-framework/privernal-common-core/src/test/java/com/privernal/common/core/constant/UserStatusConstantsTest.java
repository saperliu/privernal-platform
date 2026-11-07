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

package com.privernal.common.core.constant;

import com.privernal.common.core.constant.UserConstants;
import com.privernal.common.core.constant.DataConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户状态取值的行为基线。
 *
 * 这几组值原本是 UserStatus 枚举的 code，登录校验（停用/待审核/已删除）靠字符串比较生效。
 * 合并进 UserConstants 后没有任何编译期保护，写错一个数字就会让停用账号能正常登录，
 * 所以这里把值本身钉住。
 */
class UserStatusConstantsTest
{
    @Test
    @DisplayName("status 域取值与合并前的 UserStatus 枚举一致")
    void statusCodesMatchLegacyEnum()
    {
        assertEquals(3L, UserConstants.USER_STATUS_DISABLE);
        assertEquals(5L, UserConstants.USER_STATUS_APPROVE);
    }

    @Test
    @DisplayName("与 Integer 形式的 status 拼字符串后仍能匹配（登录校验就是这么比的）")
    void statusComparesAgainstStringifiedNumber()
    {
        Integer status = 3;
        assertTrue(String.valueOf(UserConstants.USER_STATUS_DISABLE).equals(status + ""));
        Long asLong = 5L;
        assertTrue(String.valueOf(UserConstants.USER_STATUS_APPROVE).equals(asLong + ""));
        Integer normal = 1;
        assertFalse(String.valueOf(UserConstants.USER_STATUS_DISABLE).equals(normal + ""));
    }

    @Test
    @DisplayName("删除标记属于 deleted_flag 域，不与 status 域混用")
    void deleteFlagIsItsOwnDomain()
    {
        assertEquals("Y", DataConstants.DATA_DELETE_FLAG_YES);
        assertEquals("N", DataConstants.DATA_DELETE_FLAG_NOT);
        // 原 UserStatus.DELETED 的 code 就是 "Y"，且实际被拿去比 deletedFlag
        assertEquals(DataConstants.DATA_DELETE_FLAG_YES,
                String.valueOf(DataConstants.DATA_DELETE_FLAG_YES));
    }

    @Test
    @DisplayName("待审核标记 examine_user 用 Y/N，与 status=5 是两回事")
    void examineUserFlagIndependent()
    {
        assertEquals("Y", UserConstants.USER_REQUIRE_APPROVE_Y);
    }
}
