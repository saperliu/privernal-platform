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

package com.privernal.system.event;

import java.io.Serializable;

/**
 * 用户停用事件（R1-8 权限治理③ 离职调岗联动）
 *
 * 用户 status 变更为停用(3)后由 system 模块在原事务内发布；
 * 跨模块联动（如流程代理规则失效）通过 @EventListener 订阅，与发布方同事务提交/回滚。
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：用户停用事件
 */
public class UserAccountDisabledEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 被停用的用户ID */
    private final String userId;

    public UserAccountDisabledEvent(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
