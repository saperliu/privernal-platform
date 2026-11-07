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

package com.privernal.flow.service;

import java.util.List;

/**
 * 工作流办理人解析服务接口
 * 结合平台组织、部门、人员、角色体系解析 warm-flow 权限标识
 *
 * @author saperliu
 */
public interface IFlowUserResolveService {

    /**
     * 展开当前登录用户的权限标识集合
     * 返回形如：[188666666666, user:188666666666, role:123, dept:456]
     * （含裸用户id，用于匹配经 convertPermissions 转换后的 flow_user 记录）
     *
     * @return 权限标识集合
     */
    List<String> expandCurrentUserFlags();

    /**
     * 将节点配置的权限标识转换为具体用户id集合
     * 支持前缀：user/role/dept/deptLeader/orgLeader/userGroup/script，无前缀视为用户id
     * <p>
     * 只拆解、不兜底：解析不出人时返回空集合，「找不到办理人」由节点级策略
     * （ext.wfsNoFoundUser）在 {@code FlowNoHandlerTaskHandler} 里按节点分流处理。
     * </p>
     *
     * @param permissions 设计器配置的权限标识集合
     * @return 具体用户id集合
     */
    List<String> convertPermissions(List<String> permissions);

    /**
     * 「交给组织管理员」策略下的兜底人：当前流程上下文组织的负责人
     *
     * @return 用户id集合，组织未配置负责人时为空
     */
    List<String> resolveFallbackOrgLeaders();
}
