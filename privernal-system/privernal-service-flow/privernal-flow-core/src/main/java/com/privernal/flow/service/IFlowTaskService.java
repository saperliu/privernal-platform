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

import com.privernal.flow.dto.FlowAdjustTaskDTO;
import com.privernal.flow.dto.FlowTaskHandleDTO;
import com.privernal.flow.dto.FlowTaskOptionDTO;
import com.privernal.flow.filter.FlowTaskFilterDTO;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;

import java.util.List;
import java.util.Map;

/**
 * 流程任务服务接口（审批办理全特性）
 *
 * @author saperliu
 */
public interface IFlowTaskService {

    /**
     * 待办任务分页（按当前用户权限标识）
     */
    Page<Task> todoPage(FlowTaskFilterDTO filter);

    /**
     * 已办任务分页
     */
    Page<HisTask> donePage(FlowTaskFilterDTO filter);

    /**
     * 数量统计：todo 待办 / done 已办 / start 我发起的
     */
    Map<String, Long> counts();

    /**
     * 审批通过
     */
    Instance approve(FlowTaskHandleDTO handleDTO);

    /**
     * 驳回（退回上一节点/发起节点/指定节点）
     */
    Instance reject(FlowTaskHandleDTO handleDTO);

    /**
     * 转办（更换办理人，原办理人不可再办理）
     */
    boolean transfer(FlowTaskHandleDTO handleDTO);

    /**
     * 委托（委派他人办理，办理后回到委托人）
     */
    boolean depute(FlowTaskHandleDTO handleDTO);

    /**
     * 加签（增加办理人）
     */
    boolean addSignature(FlowTaskHandleDTO handleDTO);

    /**
     * 减签（减少办理人）
     */
    boolean reductionSignature(FlowTaskHandleDTO handleDTO);

    /**
     * 跳转（任意跳转到目标节点）
     */
    Instance jump(FlowTaskHandleDTO handleDTO);

    /**
     * 暂存任务（不流转）
     */
    Instance pending(FlowTaskHandleDTO handleDTO);

    /**
     * 拿回（已办任务取回到待办）
     */
    Instance retrieve(FlowTaskHandleDTO handleDTO);

    /**
     * 实例审批记录
     */
    List<HisTask> records(Long instanceId);

    /**
     * 任务办理选项（驳回/跳转可选节点、减签可选办理人，仅任务办理人可查）
     */
    FlowTaskOptionDTO taskOptions(Long taskId);

    /**
     * 任务签收（将任务分配给当前用户）
     */
    Instance claim(FlowTaskHandleDTO handleDTO);

    /**
     * 更换审批人（直接更换任务办理人，不保留原办理人）
     */
    Instance changePerson(FlowTaskHandleDTO handleDTO);

    /**
     * 管理员调整任务（更换审批人或跳转到指定节点）
     */
    Instance adjust(FlowAdjustTaskDTO adjustDTO);

    /**
     * 超时任务扫描与自动处理
     */
    void handleTimeoutTasks();
}
