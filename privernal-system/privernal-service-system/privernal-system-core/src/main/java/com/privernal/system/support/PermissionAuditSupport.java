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

import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.domain.SystemRole;
import com.privernal.system.domain.SystemUser;
import com.privernal.system.dto.RemoteNameResolveDTO;
import com.privernal.system.dto.RemoteNameResolveResultDTO;
import com.privernal.system.mapper.SystemRoleMapper;
import com.privernal.system.mapper.SystemUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限审计支撑（R1-8 权限治理②）：用户/角色名称批量解析。
 * 供 inner HTTP 端点与 RemoteUserServiceImpl（feign 契约实现）共用，避免两处逻辑漂移。
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：名称批量解析
 */
@Component
public class PermissionAuditSupport {

    @Autowired
    private SystemUserMapper systemUserMapper;

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    /**
     * 名称批量解析：用户 id→昵称(loginName)，角色 id→角色名；未命中的 id 不在结果里（调用方原样展示）
     */
    public RemoteNameResolveResultDTO resolveNames(RemoteNameResolveDTO resolveDTO) {
        RemoteNameResolveResultDTO result = new RemoteNameResolveResultDTO();
        Map<String, String> userNames = new HashMap<>();
        Map<String, String> roleNames = new HashMap<>();
        if (resolveDTO != null) {
            List<String> userIds = distinctNotEmpty(resolveDTO.getUserIds());
            List<String> roleIds = distinctNotEmpty(resolveDTO.getRoleIds());
            if (!userIds.isEmpty()) {
                // MP 3.5.12：PrivernalMapper 重声明的 selectBatchIds 已无注入语句（废弃名），走 selectByIds
                for (SystemUser user : systemUserMapper.selectByIds(userIds)) {
                    String display = StringUtils.isBlank(user.getNickName())
                            ? user.getLoginName()
                            : user.getNickName() + "(" + user.getLoginName() + ")";
                    userNames.put(user.getId(), display);
                }
            }
            if (!roleIds.isEmpty()) {
                for (SystemRole role : systemRoleMapper.selectByIds(roleIds)) {
                    roleNames.put(role.getId(), role.getRoleName());
                }
            }
        }
        result.setUserNames(userNames);
        result.setRoleNames(roleNames);
        return result;
    }

    private List<String> distinctNotEmpty(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String id : ids) {
            if (id != null && !id.isEmpty() && seen.add(id)) {
                result.add(id);
            }
        }
        return result;
    }
}
