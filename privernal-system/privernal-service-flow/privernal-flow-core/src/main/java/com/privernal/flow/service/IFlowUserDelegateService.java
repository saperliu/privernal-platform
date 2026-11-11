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

import com.privernal.flow.domain.FlowUserDelegate;

import java.util.List;

/**
 * 流程代理规则服务（R2-17 代理与离职移交）
 *
 * @author saperliu
 */
public interface IFlowUserDelegateService {

    /**
     * 代理规则列表（organizationId 非空时限定组织；userId 非空时限定被代理人）
     */
    List<FlowUserDelegate> list(String organizationId, String userId);

    /**
     * 保存代理规则（校验人员存在、重复启用规则）
     */
    FlowUserDelegate save(FlowUserDelegate delegate);

    /**
     * 修改代理规则（启停/改期/换代理人）
     */
    FlowUserDelegate edit(FlowUserDelegate delegate);

    /**
     * 删除（按 id 批量）
     */
    int remove(List<String> ids);
}
