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

/**
 * 用户常量信息
 *
 * @author saperliu
 */
public class UserConstants {

    /**
     * 用户状态（对应 system_user_base.status，与 DTO 的 Long 字段同型）。
     * 正常=1 停用=3 待审核=5；删除标记不在此域，用 DATA_DELETE_FLAG_* 表达。
     */
    public static final Long USER_STATUS_DISABLE = 3L;
    public static final Long USER_STATUS_APPROVE = 5L;

    /**
     * 校验返回结果码
     */
    public static final Integer NOT_UNIQUE_EMAIL = 10001;
    public static final Integer NOT_UNIQUE_MOBILE = 10002;
    public static final Integer NOT_UNIQUE_LOGIN_NAME = 10003;
    // 数据权限过大，无权添加
    public static final Integer DATA_SCOPE_MAX = 10004;
    public static final int EXIST_MOBILE = 10005;

    /**
     * 用户名长度限制
     */
    public static final int USERNAME_MIN_LENGTH = 2;

    public static final int USERNAME_MAX_LENGTH = 100;

    /**
     * 密码长度限制
     */
    public static final int PASSWORD_MIN_LENGTH = 2;

    public static final int PASSWORD_MAX_LENGTH = 200;

    /**
     * 用户需要审批,主要是小程序的用户的注册后
     */
    public static final String USER_REQUIRE_APPROVE_Y = "Y";

    /**
     * 系统本身用户id，由系统创建的数据的，created_by填写的ID
     */
    public static final String USER_TYPE_SYSTEM = "system";
    public static final String USER_TYPE_SYSTEM_NAME = "系统";

}
