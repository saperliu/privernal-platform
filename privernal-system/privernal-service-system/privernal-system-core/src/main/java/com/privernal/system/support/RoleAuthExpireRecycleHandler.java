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

import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleAction;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import com.privernal.system.domain.SystemRoleUser;
import com.privernal.system.mapper.SystemRoleUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色授权到期回收动作（R1-8 权限治理①）
 *
 * 登录装配 SQL 已按 expire_time 过滤（到期即失效），本任务做数据面收尾：
 * 把已到期的 system_role_user 行软删（回收扫描），并刷新受影响在线用户的会话权限快照。
 * 幂等：到期行回收后不再出现在扫描结果里；重复执行无副作用。
 * 注册：SystemTaskRegistrar 固定间隔（SYSTEM 来源，默认组织）。
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：授权到期回收扫描
 */
@Slf4j
@Component
@ScheduleAction(RoleAuthExpireRecycleHandler.BEAN_NAME)
public class RoleAuthExpireRecycleHandler implements ScheduleActionHandler {

    public static final String BEAN_NAME = "role-auth-expire-recycle";

    @Autowired
    private SystemRoleUserMapper systemRoleUserMapper;

    @Autowired
    private UserPermissionSessionRefresher sessionRefresher;

    @Override
    public String actionType() {
        return "BEAN";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        List<SystemRoleUser> expiredList = systemRoleUserMapper.selectExpiredActiveList();
        if (expiredList == null || expiredList.isEmpty()) {
            return ActionResult.ok("0/0");
        }
        int recycled = 0;
        Set<String> userIds = new LinkedHashSet<>();
        for (SystemRoleUser roleUser : expiredList) {
            try {
                systemRoleUserMapper.deleteFillById(roleUser.getId());
                userIds.add(roleUser.getUserId());
                recycled++;
            } catch (Exception e) {
                log.warn("[R1-8] 到期授权回收失败 id={} userId={}：{}", roleUser.getId(), roleUser.getUserId(), e.getMessage());
            }
        }
        for (String userId : userIds) {
            sessionRefresher.refresh(userId);
        }
        log.info("[R1-8] 到期授权回收完成：回收 {} 行，涉及 {} 个用户", recycled, userIds.size());
        return ActionResult.ok(recycled + "/" + userIds.size());
    }
}
