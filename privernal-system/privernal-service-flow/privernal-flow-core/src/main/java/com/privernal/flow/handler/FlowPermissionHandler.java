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

package com.privernal.flow.handler;

import com.privernal.common.security.service.SecurityUtils;
import com.privernal.flow.service.IFlowUserResolveService;
import org.dromara.warm.flow.core.handler.PermissionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * warm-flow 办理人权限处理器
 * 注册为 Spring Bean 后由引擎自动拾取（FrameInvoker.getBean）
 *
 * @author saperliu
 */
@Component
public class FlowPermissionHandler implements PermissionHandler {

    @Autowired
    private IFlowUserResolveService flowUserResolveService;

    /**
     * 当前用户权限标识集合（用于办理权限校验与待办匹配）
     */
    @Override
    public List<String> permissions() {
        return flowUserResolveService.expandCurrentUserFlags();
    }

    /**
     * 当前办理人（用户id）
     */
    @Override
    public String getHandler() {
        return SecurityUtils.getUserId();
    }

    /**
     * 转换办理人：把设计器预设的 role:/dept:/deptLeader: 等标识转换为具体用户id
     */
    @Override
    public List<String> convertPermissions(List<String> permissions) {
        return flowUserResolveService.convertPermissions(permissions);
    }
}
