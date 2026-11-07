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

package com.privernal.common.security.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.privernal.common.core.constant.CacheConstants;
import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.constant.RoleConstants;
import com.privernal.common.core.utils.ServletUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.dto.UserInfo;
import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 权限获取工具类
 *
 * @author saperliu
 */
@Slf4j
public class SecurityUtils {

    public static String NOT_FIELD = "NOT_COMPARE";

    public static final String HEADER_ORGANIZATION_ID = "organization-id";

    /**
     * 获取当前用户
     */
    public static UserInfo getUserInfo() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object json = saSession.get(Constants.USER_INFO_SESSION);
                if (json != null) {
                    UserInfo userInfo = JSONObject.parseObject(json.toString(), UserInfo.class);
                    return userInfo;
                }
                return null;
            }
        } catch (Exception e) {
            log.error("get current user info error ", e);
            log.info(" get UserInfo  is NotWebContextException");
        }
        return null;
    }

    /**
     * 获取用户登录名
     */
    public static String getLoginName() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object username = saSession.get(CacheConstants.DETAILS_LOGIN_NAME);
                if (username != null) {
                    return username.toString();
                } else {
                    return "";
                }
            }
        } catch (Exception e) {
            log.error("get login name error ", e);
        }
        return "";
    }

    /**
     * 获取用户ID
     */
    public static String getUserId() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object userId = saSession.get(CacheConstants.DETAILS_USER_ID);
                if (userId != null) {
                    return userId.toString();
                } else {
                    return "";
                }
            }
        } catch (Exception e) {
            log.error("get current user id error ", e);
            log.info(" get user id  is NotWebContextException");
        }
        return "";

    }

    /**
     * 获取用户昵称
     */
    public static String getNickName() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object json = saSession.get(Constants.USER_INFO_SESSION);
                if (json != null) {
                    UserInfo userInfo = JSONObject.parseObject(json.toString(), UserInfo.class);
                    return userInfo.getNickName();
                }
                return "";
            }
        } catch (Exception e) {
            log.error("get current nick name  error ", e);
        }
        return "";
    }

    /**
     * 获取当前用户的组织
     */
    public static String getOrganizationId() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object json = saSession.get(Constants.USER_INFO_SESSION);
                if (json != null) {
                    UserInfo userInfo = JSONObject.parseObject(json.toString(), UserInfo.class);
                    return userInfo.getOrganizationId();
                }
                return "";
            }
        } catch (Exception e) {
            log.error("get current user organization id error ", e);
            log.info(" get organization id  is NotWebContextException");
        }
        return "";
    }

    /**
     * 获取当前用户的组织名称
     */
    public static String getOrganizationName() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object json = saSession.get(Constants.USER_INFO_SESSION);
                if (json != null) {
                    UserInfo userInfo = JSONObject.parseObject(json.toString(), UserInfo.class);
                    return userInfo.getOrganizationName();
                }
                return "";
            }
        } catch (Exception e) {
            log.error("get current user organization name error ", e);
        }
        return "";
    }

    /**
     * 获取当前用户的组织编码
     */
    public static String getOrganizationCode() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object json = saSession.get(Constants.USER_INFO_SESSION);
                if (json != null) {
                    UserInfo userInfo = JSONObject.parseObject(json.toString(), UserInfo.class);
                    return userInfo.getOrganizationCode();
                }
                return "";
            }
        } catch (Exception e) {
            log.error("get current user organization code error ", e);
        }
        return "";
    }

    /**
     * 获取当前用户的部门
     */
    public static String getDepartmentId() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object json = saSession.get(Constants.USER_INFO_SESSION);
                if (json != null) {
                    UserInfo userInfo = JSONObject.parseObject(json.toString(), UserInfo.class);
                    return userInfo.getDepartmentId();
                }
                return "";
            }
        } catch (Exception e) {
            log.error("get current department id error ", e);
        }
        return "";
    }

    /**
     * 获取身份证
     */
    public static String getIdentityNum() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object json = saSession.get(Constants.USER_INFO_SESSION);
                if (json != null) {
                    UserInfo userInfo = JSONObject.parseObject(json.toString(), UserInfo.class);
                    return userInfo.getIdentityNum();
                }
                return "";
            }
        } catch (Exception e) {
            log.error("get current user identity num error ", e);
        }
        return "";
    }

    /**
     * 获取手机号
     */
    public static String getMobile() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object json = saSession.get(Constants.USER_INFO_SESSION);
                if (json != null) {
                    UserInfo userInfo = JSONObject.parseObject(json.toString(), UserInfo.class);
                    return userInfo.getPhone();
                }
                return "";
            }
        } catch (Exception e) {
            log.error("get current mobile error ", e);
        }
        return "";
    }

    /**
     * 获取请求token
     */
    public static String getToken() {
        return StpUtil.getTokenValue();
    }

    /**
     * 根据request获取请求token
     */
    public static String getToken(HttpServletRequest request) {
        String token = ServletUtils.getRequest().getHeader(CacheConstants.HEADER);
        if (StringUtils.isNotEmpty(token) && token.startsWith(CacheConstants.TOKEN_PREFIX)) {
            token = token.replace(CacheConstants.TOKEN_PREFIX, "");
        }
        return token;
    }

    /**
     * 是否为组织管理员
     *
     * @return 结果
     */
    public static boolean isSuperRoleUser() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object json = saSession.get(Constants.USER_INFO_SESSION);
                if (json != null) {
                    UserInfo userInfo = JSONObject.parseObject(json.toString(), UserInfo.class);
                    Set<String> stringSet = userInfo.getRoleKeys();
                    return stringSet.contains(RoleConstants.SUPER_ROLE_ADMIN);
                }
                return false;
            }
        } catch (Exception e) {
            log.error("current is super role user check error ", e);
        }
        return false;
    }

    /**
     * 是否为平台管理员
     *
     * @return 结果
     */
    public static boolean isSuperPlatformRoleUser() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object json = saSession.get(Constants.USER_INFO_SESSION);
                if (json != null) {
                    UserInfo userInfo = JSONObject.parseObject(json.toString(), UserInfo.class);
                    Set<String> stringSet = userInfo.getRoleKeys();
                    return stringSet.contains(RoleConstants.SUPER_PLATFORM_ROLE_ADMIN);
                }
                return false;
            }
        } catch (Exception e) {
            log.error("current is super role user check error ", e);
        }
        return false;
    }

    /**
     * 检查数据权限
     *
     * @param organizationId 组织ID
     * @param departmentId   部门ID
     * @return 结果
     */
    public static boolean checkDataScope(String organizationId, String departmentId) {
        boolean flagOrg = false;
        boolean flagDept = false;
        if (!NOT_FIELD.equals(organizationId)) {
            if (getOrganizationId().equals(organizationId)) {
                flagOrg = true;
            }
        } else {
            flagOrg = true;
        }
        if (!NOT_FIELD.equals(departmentId)) {
            if (getDepartmentId().equals(departmentId)) {
                flagDept = true;
            }
        } else {
            flagDept = true;
        }
        return flagOrg && flagDept;
    }

    /**
     * 生成BCryptPasswordEncoder密码
     *
     * @param password 密码
     * @return 加密字符串
     */
    public static String encryptPassword(String password) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > 72) {
            password = password.substring(0, 72);
        }
        return passwordEncoder.encode(password);
    }

    /**
     * 判断密码是否相同
     *
     * @param rawPassword     真实密码
     * @param encodedPassword 加密后字符
     * @return 结果
     */
    public static boolean matchesPassword(String rawPassword, String encodedPassword) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 取值的后6位为默认密码
     *
     * @param fieldValue 数据
     * @return 后6位
     */
    public static String getDefaultPassword(String fieldValue) {

        return fieldValue.substring(fieldValue.length() - 6);
    }

    public static String getMd5Password(String password) {
        Digester md5 = new Digester(DigestAlgorithm.SHA512);
        return md5.digestHex(password);
    }

    /**
     * 检查用户是否登录
     *
     * @param userId 用户ID
     * @return 是/否
     */
    public static boolean checkLogin(String userId) {
        return StpUtil.isLogin(userId);
    }

    /**
     * 检查用户是否登录
     *
     * @param userId 用户ID
     * @param token  token
     * @return 是/否
     */
    public static boolean checkTokenLogin(String userId, String token) {
        return StringUtils.isNotEmpty(userId) && userId.equals(StpUtil.getLoginIdByToken(token));
    }

    /**
     * 获取数据库名编码
     */
    public static String getDatabaseName() {
        try {
            SaSession saSession = StpUtil.getTokenSession();
            if (saSession != null) {
                Object userId = saSession.get(CacheConstants.ORGANIZATION_DB_NAME);
                if (userId != null) {
                    return userId.toString();
                } else {
                    return "";
                }
            }
        } catch (Exception e) {
            log.error("get current user id error ", e);
        }
        return "";
    }
}
