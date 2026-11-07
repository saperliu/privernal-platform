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
 * 通用常量信息
 *
 * @author saperliu
 */
public class Constants
{
    /**
     * UTF-8 字符集
     */
    public static final String UTF8 = "UTF-8";

    /**
     * GBK 字符集
     */
    public static final String GBK = "GBK";

    /**
     * 成功标记
     */
    public static final Integer SUCCESS = 200;

    /**
     * 失败标记
     */
    public static final Integer FAIL = 500;
    /**
     * 授权失败，缺少token或token错误
     */
    public static final Integer AUTH_FAIL = 401;

    /**
     * 登录成功
     */
    public static final String LOGIN_SUCCESS = "Success";

    // 成功
    public static final Long LOGIN_SUCCESS_STATUS = 1L;
    // 失败
    public static final Long LOGIN_FAIL_STATUS = 0L;

    /**
     * 注销
     */
    public static final String LOGOUT = "Logout";

    /**
     * 注册
     */
    public static final String REGISTER = "Register";

    /**
     * 登录失败
     */
    public static final String LOGIN_FAIL = "Error";

    /**
     * 当前记录起始索引
     */
    public static final String PAGE_NUM = "pageNum";

    /**
     * 每页显示记录数
     */
    public static final String PAGE_SIZE = "pageSize";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /**
     * 验证码有效期（分钟）
     */
    public static final long CAPTCHA_EXPIRATION = 2;

    /**
     * 令牌有效期（分钟）
     */
    public static final long TOKEN_EXPIRE = 720;

    /**
     * session上的用户信息 SA-TOKEN的存储session
     */
    public static final String USER_INFO_SESSION = "USER_INFO_SESSION";

    /**
     * 用户来源, 微信小程序
     */
    public static final String USER_SOURCE_WX_MINI_PROGRAM = "miniprogram";

    /**
     * 存储登录的用户的id
     */
    public static final String USER_LOGIN_IDS = "USER_LOGIN_IDS";
    /**
     * 存储登录的用户信息 业务数据里的
     */
    public static final String USER_LOGIN_USER_INFO = "USER_LOGIN_USER_INFO";

    /**
     * 请求来源
     */
    public static final String SERVICE_REQUEST = "SERVICE_REQUEST";

    /** 短信验证码缓存键前缀（登录/重置密码共用；S3 整改后重置密码端点自行校验并消费） */
    public static final String SMS_CODE_PREFIX = "SMS_CODE_";

    /** 接口限流计数键前缀（@RateLimiter / RateLimiterAspect） */
    public static final String RATE_LIMITER_PREFIX = "RATE_LIMITER:";

    /** F4 登录挑战一次性 nonce 缓存键前缀（60 秒/单次，读取即作废） */
    public static final String PWD_CHALLENGE_PREFIX = "PWD_CHALLENGE_";

    /** F4 挑战登录返回码：口令凭证待迁移（客户端自动降级引导一次后重试挑战登录） */
    public static final int CODE_PWD_MIGRATE_REQUIRED = 40101;

    /**
     * 内部请求
     */
    public static final String REQUEST_INNER = "INNER";

}
