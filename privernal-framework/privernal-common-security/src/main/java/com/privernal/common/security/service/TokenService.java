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
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.temp.SaTempUtil;
import com.privernal.common.core.constant.CacheConstants;
import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.utils.ServletUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.core.utils.ip.IpUtils;
import com.privernal.system.dto.UserInfo;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * token验证处理
 *
 * @author saperliu
 */
@Component
public class TokenService {

    private final static long EXPIRE_TIME = Constants.TOKEN_EXPIRE * 60;

    private final static String ACCESS_TOKEN = CacheConstants.LOGIN_TOKEN_KEY;

    protected static final long MILLIS_SECOND = 1000;


    /**
     * 创建令牌
     */
    public Map<String, Object> createToken(UserInfo userInfo) {

        // 获取登录token
        StpUtil.login(userInfo.getUserId());
        // 兼容标准的Oath2属性
        SaTokenInfo saTokenInfo = StpUtil.getTokenInfo();
        // 生成token

        String token = saTokenInfo.tokenValue;
        userInfo.setToken(token);
        userInfo.setIpAddr(IpUtils.getIpAddr(ServletUtils.getRequest()));
        userInfo.setExpireTime(saTokenInfo.tokenTimeout);
        userInfo.setNickName(userInfo.getNickName());
        userInfo.setLoginName(userInfo.getLoginName());
        userInfo.setLoginTime(System.currentTimeMillis());
//        refreshToken(userInfo);
        userInfo.setPassword("");
        // 保存或更新用户token
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("token", token);
        map.put("expireTime", saTokenInfo.tokenTimeout);
        map.put("userInfo", userInfo);

        setLoginUser(userInfo);
        return map;
    }

    /**
     * 创建临时令牌
     */
    public Map<String, Object> createTempToken(UserInfo userInfo) {

        // 获取登录token
        String token  = SaTempUtil.createToken(JSON.toJSONString(userInfo),120);

        // 保存或更新用户token
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("token", token);
        map.put("expireTime", 120);
        map.put("organizations", userInfo.getUserServiceOrgDTOList());
        return map;
    }

    /**
     * 获取用户身份信息
     *
     * @return 用户信息
     */
    public UserInfo getLoginUser() {
        // 获取请求携带的令牌
        SaSession saSession = StpUtil.getTokenSession();
        String s = saSession.getString(Constants.USER_INFO_SESSION);
        return JSON.parseObject(s, UserInfo.class);
    }

    /**
     * 退出登录
     * @param token
     */
    public void delLoginUser(String token) {
        StpUtil.checkLogin();
        StpUtil.logout();
    }

    /**
     * 设置用户身份信息
     */
    public void setLoginUser(UserInfo userInfo) {
        if (StringUtils.isNotNull(userInfo) && StringUtils.isNotEmpty(userInfo.getToken())) {
            SaSession saSession = StpUtil.getTokenSession();
            saSession.set(Constants.USER_INFO_SESSION, JSON.toJSONString(userInfo));
            saSession.set(CacheConstants.DETAILS_LOGIN_NAME, userInfo.getLoginName());
            saSession.set(CacheConstants.DETAILS_USER_ID, userInfo.getUserId());
        }
    }

    public void setDatabase(String databaseName) {
        SaSession saSession = StpUtil.getTokenSession();
        saSession.set(CacheConstants.ORGANIZATION_DB_NAME, databaseName);
    }

    /**
     * 刷新令牌有效期
     *
     * @param userInfo 登录信息
     */
    public void refreshToken(UserInfo userInfo) {
        // 先检查是否已过期
        StpUtil.checkActiveTimeout();
        // 检查通过后继续续签
        StpUtil.updateLastActiveToNow();
    }

    private String getTokenKey(String token) {
        return ACCESS_TOKEN + token;
    }
}
