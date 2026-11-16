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

package com.privernal.flow.mapper;

import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.flow.domain.FlowUserDelegate;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 流程代理规则Mapper（R2-17，按组织拦截器隔离，不参与部门数据权限）
 * <p>@DS("master") 钉在 mapper 层：convertPermissions 代理钩子在引擎任务创建回调内执行，
 * 调用链上并非处处有服务级 @DS 上下文（如事件监听线程）。</p>
 *
 * @author saperliu
 */
@DS("master")
@IgnoreDataScope
public interface FlowUserDelegateMapper extends PrivernalMapper<FlowUserDelegate> {

    /**
     * 查询指定用户当前生效的代理规则（status=启用 且 now 在有效期内，时间空视为长期）
     */
    List<FlowUserDelegate> selectActiveByUserIds(@Param("userIds") List<String> userIds, @Param("now") Date now);

    /**
     * 查询指定代理人当前生效的被代理用户id（待办可见性扩展用）
     */
    List<String> selectDelegatedUserIdsByAgent(@Param("agentId") String agentId, @Param("now") Date now);

    /**
     * R1-8 离职停用联动：停用即失效其作为被代理人/代理人的全部规则（软删）
     *
     * @param userId 被停用用户id
     * @param now    停用时间
     * @return 失效条数
     */
    int disableByUserId(@Param("userId") String userId, @Param("now") Date now);
}
