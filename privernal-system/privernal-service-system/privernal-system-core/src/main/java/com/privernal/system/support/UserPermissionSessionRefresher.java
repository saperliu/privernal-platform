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

package com.privernal.system.support;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.dto.UserInfo;
import com.privernal.system.service.ISystemUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户会话权限快照刷新器（R1-8 权限治理）
 *
 * 权限在登录/getInfo 时以 JSON 快照写入 sa-token token session（TokenService.setLoginUser），
 * 请求时只读快照不回库。角色授权发生治理动作（到期回收/有效期修改/停用）后，
 * 在线用户的旧快照需要重装配才能即时生效——本组件按 userId 找到其在线 token，
 * 在原组织下重新装配权限并原位写回（不在请求线程内，直接操作 token session）。
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：会话快照刷新
 */
@Slf4j
@Component
public class UserPermissionSessionRefresher {

    @Autowired
    private ISystemUserService systemUserService;

    /**
     * 刷新在线用户的权限快照；用户不在线（无 token）则跳过。失败只告警，不影响调用方事务。
     * 兼容请求线程与调度内核线程：sa-token 1.45 的 StpUtil 调用依赖 SaTokenContext，
     * 内核线程用 SaTokenContextMockUtil 挂模拟上下文。
     *
     * @param userId 用户ID
     * @return 是否实际刷新了快照
     */
    public boolean refresh(String userId) {
        if (StringUtils.isEmpty(userId)) {
            return false;
        }
        boolean mockContextAttached = false;
        try {
            // 仅在无请求上下文的线程（调度内核）挂模拟上下文；请求线程保留真实上下文
            if (cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff.getModelBoxOrNull() == null) {
                cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
                mockContextAttached = true;
            }
            String token = StpUtil.getTokenValueByLoginId(userId);
            if (StringUtils.isEmpty(token)) {
                return false;
            }
            SaSession session = StpUtil.getTokenSessionByToken(token);
            String snapshot = session.getString(Constants.USER_INFO_SESSION);
            if (StringUtils.isEmpty(snapshot)) {
                return false;
            }
            UserInfo old = JSON.parseObject(snapshot, UserInfo.class);
            if (old == null || StringUtils.isEmpty(old.getOrganizationId())) {
                return false;
            }
            SystemUserDTO sysUser = systemUserService.selectUserDTOByOrgIdAndId(userId, old.getOrganizationId());
            if (sysUser == null) {
                return false;
            }
            UserInfo fresh = systemUserService.getUserInfo(sysUser, old.getOrganizationId());
            fresh.setToken(token);
            session.set(Constants.USER_INFO_SESSION, JSON.toJSONString(fresh));
            log.info("[R1-8] 已刷新用户会话权限快照 userId={}", userId);
            return true;
        } catch (Exception e) {
            log.warn("[R1-8] 刷新用户会话权限快照失败 userId={}：{}", userId, e.getMessage());
            return false;
        } finally {
            if (mockContextAttached) {
                cn.dev33.satoken.context.mock.SaTokenContextMockUtil.clearContext();
            }
        }
    }
}
