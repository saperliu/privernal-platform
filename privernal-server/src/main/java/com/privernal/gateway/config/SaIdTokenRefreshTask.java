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

package com.privernal.gateway.config;

import cn.dev33.satoken.same.SaSameUtil;
import org.springframework.context.annotation.Configuration;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2021-08-30 23:35
 * @Description 主动刷新Id-Token，保证其高可用
 *
 * 已迁移到定时任务模块（SYSTEM 存量任务迁入）：SYSTEM 任务
 * same-token-refresh 以 FIXED_DELAY 300s 等价接管（BEAN 动作 SameTokenRefreshActionHandler）。
 * 原 @Scheduled 移除——否则与任务表链路双跑。回滚方式：恢复本类 @Scheduled 并
 * 在任务管理页暂停 same-token-refresh 任务。
 */
@Configuration
public class SaIdTokenRefreshTask {

    /** 保留为手动兜底入口（不再自动调度） */
    public void refreshToken(){
        SaSameUtil.refreshToken();
    }
}
