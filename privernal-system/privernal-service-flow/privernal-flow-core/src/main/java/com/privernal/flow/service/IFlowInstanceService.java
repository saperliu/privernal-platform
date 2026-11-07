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

import com.privernal.flow.dto.FlowInstanceStatusDTO;
import com.privernal.flow.dto.FlowNodeInfoDTO;
import com.privernal.flow.dto.FlowStartDTO;
import com.privernal.flow.filter.FlowInstanceFilterDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;

import java.util.List;

/**
 * 流程实例服务接口
 *
 * @author saperliu
 */
public interface IFlowInstanceService {

    /**
     * 启动流程（支持 flowCode 或 tableCode 定位定义）
     */
    FlowInstanceStatusDTO start(FlowStartDTO startDTO);

    /**
     * 实例分页（onlyMine=true 时仅当前用户发起的）
     */
    Page<Instance> pageInstances(FlowInstanceFilterDTO filter, boolean onlyMine);

    /**
     * 按业务id查询最新实例
     */
    Instance getByBusinessId(String businessId);

    /**
     * 按业务id查询流程状态
     */
    FlowInstanceStatusDTO getStatusByBusinessId(String businessId);

    /**
     * 当前登录人是否为该业务数据所在流程实例的参与人（发起人、曾任办理人/协办人、当前待办人）。
     * 参与人未必持有业务表的按钮权限，但要能查看自己经手的申请单，动态数据只读放行以此为准。
     *
     * @param tableCode  调用方声称的业务表编码，需与实例的 businessId 解析出的表一致
     * @param businessId 业务数据id
     */
    boolean isCurrentUserParticipant(String tableCode, String businessId);

    /**
     * 按实例id查询流程状态（不存在时返回 null）
     */
    FlowInstanceStatusDTO getStatusByInstanceId(Long instanceId);

    /**
     * 终止（停止）流程实例
     */
    FlowInstanceStatusDTO terminate(Long instanceId, String message);

    /**
     * 终止（停止）流程实例（内部服务调用无登录态时显式指定操作人）
     */
    FlowInstanceStatusDTO terminate(Long instanceId, String message, String handler);

    /**
     * 撤销流程（发起人撤回）
     */
    FlowInstanceStatusDTO revoke(Long instanceId, String message);

    /**
     * 激活实例
     */
    boolean active(Long instanceId);

    /**
     * 挂起实例
     */
    boolean unActive(Long instanceId);

    /**
     * 实例审批记录
     */
    List<HisTask> records(Long instanceId);

    /**
     * 按业务id查询流程定义
     */
    Definition getDefinitionByBusinessId(String businessId);

    /**
     * 同步业务数据状态（按绑定关系回写动态表单数据 status 列）
     */
    void syncBusinessStatus(Long instanceId);

    /**
     * 重启流程（终止旧实例并基于相同业务数据/流程定义启动新实例）
     */
    FlowInstanceStatusDTO restart(Long instanceId, String message);

    /**
     * 获取流程实例的节点信息列表（用于流程图高亮回显）
     */
    List<FlowNodeInfoDTO> getInstanceNodes(Long instanceId);
}
