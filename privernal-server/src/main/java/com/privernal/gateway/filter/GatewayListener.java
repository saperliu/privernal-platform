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

package com.privernal.gateway.filter;

import cn.dev33.satoken.listener.SaTokenListener;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.privernal.common.core.constant.Constants;
import com.privernal.common.redis.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2021-09-11 8:00
 * @Description 侦听器的实现
 */
@Component
public class GatewayListener implements SaTokenListener {

    private static final Logger log = LoggerFactory.getLogger(GatewayListener.class);

    @Autowired
    private RedisService redisService;

    /**
     * 每次登录时触发
     */
    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter saLoginParameter) {
        log.info(" login   {}  {}  {}", loginType, loginId, saLoginParameter);

        redisService.setCacheObject(Constants.USER_LOGIN_IDS+":"+loginId,loginId);
    }

    /** 每次Token续期时触发 */
    @Override
    public void doRenewTimeout(String loginType, Object loginId, String s1, long l) {
        log.info(" doRenewTimeout   {}  {}  {}  {}", loginType, loginId, s1, l);
    }

    /**
     * 每次注销时触发
     */
    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        log.info(" logout   {}  {}  {}", loginType, loginId, tokenValue);
        redisService.deleteObject(Constants.USER_LOGIN_IDS+":"+loginId);
        redisService.deleteObject(Constants.USER_LOGIN_USER_INFO+":"+loginId);
    }

    /**
     * 每次被踢下线时触发
     */
    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        log.info(" doKickout   {}  {}  {}   ", loginType, loginId, tokenValue);
        redisService.deleteObject(Constants.USER_LOGIN_IDS+":"+loginId);
        redisService.deleteObject(Constants.USER_LOGIN_USER_INFO+":"+loginId);
    }
    /**
     * 每次被顶下线时触发
     */
    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        log.info(" Replaced   {}  {}  {}", loginType, loginId, tokenValue);
        redisService.deleteObject(Constants.USER_LOGIN_IDS+":"+loginId);
        redisService.deleteObject(Constants.USER_LOGIN_USER_INFO+":"+loginId);
    }

    /**
     * 每次被封禁时触发
     */
    @Override
    public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
        log.info(" Disable   {}  {}  {}", loginType, loginId, disableTime);
        redisService.deleteObject(Constants.USER_LOGIN_IDS+":"+loginId);
        redisService.deleteObject(Constants.USER_LOGIN_USER_INFO+":"+loginId);
    }

    /**
     * 每次被解封时触发
     */
    @Override
    public void doUntieDisable(String loginType, Object loginId, String service) {
        log.info(" UntieDisable   {}  {} ", loginType, loginId);
        redisService.deleteObject(Constants.USER_LOGIN_IDS+":"+loginId);
        redisService.deleteObject(Constants.USER_LOGIN_USER_INFO+":"+loginId);
    }

    /** 每次二级认证时触发 */
    @Override
    public void doOpenSafe(String loginType, String tokenValue, String service, long safeTime) {

    }

    /** 每次退出二级认证时触发 */
    @Override
    public void doCloseSafe(String loginType, String tokenValue, String service) {

    }

    /**
     * 每次创建Session时触发
     */
    @Override
    public void doCreateSession(String id) {
        log.info(" Session  create   {}", id);
    }

    /**
     * 每次注销Session时触发
     */
    @Override
    public void doLogoutSession(String id) {
        log.info(" Session  logout  {}", id);
    }
}
