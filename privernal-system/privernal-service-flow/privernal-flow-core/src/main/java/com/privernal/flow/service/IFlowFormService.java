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

import com.privernal.flow.domain.FlowFormBind;
import com.privernal.flow.domain.FlowFormNodeConfig;
import com.privernal.flow.dto.FlowFormPermissionDTO;
import org.dromara.warm.flow.core.entity.Instance;

import java.util.List;

/**
 * 流程表单服务接口
 * 流程定义与动态表单的绑定、节点字段/按钮权限配置与运行时解析
 *
 * @author saperliu
 */
public interface IFlowFormService {

    /**
     * 查询绑定列表（分页由调用方 PageHelper 控制）
     */
    List<FlowFormBind> selectBindList(FlowFormBind query);

    /**
     * 按动态表单编码查询绑定
     */
    FlowFormBind findBindByTableCode(String tableCode);

    /**
     * 按流程编码查询绑定
     */
    FlowFormBind findBindByFlowCode(String flowCode);

    /**
     * 新增绑定
     */
    boolean saveBind(FlowFormBind bind);

    /**
     * 修改绑定
     */
    boolean updateBind(FlowFormBind bind);

    /**
     * 删除绑定
     */
    boolean deleteBindByIds(String[] ids);

    /**
     * 按流程编码删除绑定及节点配置（流程定义删除时联动）
     */
    boolean deleteByFlowCode(String flowCode);

    /**
     * 查询流程的节点表单配置列表
     */
    List<FlowFormNodeConfig> selectNodeConfigList(String definitionCode);

    /**
     * 保存节点表单配置（definition_code + node_code 存在则更新）
     */
    boolean saveNodeConfig(FlowFormNodeConfig nodeConfig);

    /**
     * 运行时表单权限（当前节点字段/按钮权限 + 当前用户是否审批人）
     */
    FlowFormPermissionDTO getRuntimePermission(String tableCode, String businessId);

    /**
     * 当前登录人是否为实例待办任务的办理人（含转办、委托），即「可办理」身份
     */
    boolean isCurrentUserHandler(Instance instance);
}
