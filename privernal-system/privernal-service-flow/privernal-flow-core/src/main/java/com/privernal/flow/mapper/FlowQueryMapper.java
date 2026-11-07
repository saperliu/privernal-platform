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
import com.privernal.flow.dto.FlowTodoTaskExtDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.dromara.warm.flow.orm.entity.FlowTask;

import java.util.List;

/**
 * warm-flow 引擎表扩展查询 Mapper（分页/待办/已办等，引擎未提供的查询）
 * 引擎表按组织拦截器忽略 + 权限标识过滤，不参与部门数据权限
 *
 * @author saperliu
 */
@IgnoreDataScope
@Mapper
public interface FlowQueryMapper {

    /**
     * 待办任务分页（按当前用户权限标识交集匹配 flow_user）
     */
    /**
     * 流程标题回写：实例
     */
    int updateInstanceFlowName(@Param("instanceId") Long instanceId, @Param("flowName") String flowName);

    /**
     * 流程标题回写：实例下全部待办任务
     */
    int updateTaskFlowNameByInstance(@Param("instanceId") Long instanceId, @Param("flowName") String flowName);

    Page<com.privernal.flow.dto.FlowTaskListDTO> selectTodoPage(Page<FlowTask> page,
                                  @Param("flags") List<String> flags,
                                  @Param("flowName") String flowName,
                                  @Param("businessId") String businessId,
                                  @Param("nodeName") String nodeName,
                                  @Param("timeStart") String timeStart,
                                  @Param("timeEnd") String timeEnd);

    /**
     * 已办任务分页（审批人或协作人为当前用户）
     */
    Page<com.privernal.flow.dto.FlowHisTaskListDTO> selectDonePage(Page<FlowHisTask> page,
                                     @Param("userId") String userId,
                                     @Param("flowName") String flowName,
                                     @Param("businessId") String businessId,
                                     @Param("timeStart") String timeStart,
                                     @Param("timeEnd") String timeEnd);

    /**
     * 流程实例分页（createBy 为空时查询全部，供管理端使用）
     */
    Page<com.privernal.flow.dto.FlowInstanceListDTO> selectInstancePage(Page<FlowInstance> page,
                                          @Param("createBy") String createBy,
                                          @Param("flowCode") String flowCode,
                                          @Param("flowName") String flowName,
                                          @Param("businessId") String businessId,
                                          @Param("flowStatus") String flowStatus,
                                          @Param("timeStart") String timeStart,
                                          @Param("timeEnd") String timeEnd);

    /**
     * 流程定义分页
     */
    Page<FlowDefinition> selectDefinitionPage(Page<FlowDefinition> page,
                                              @Param("flowCode") String flowCode,
                                              @Param("flowName") String flowName,
                                              @Param("category") String category,
                                              @Param("isPublish") Integer isPublish);

    /**
     * 按业务id查询进行中的流程实例（排除终态）
     */
    List<FlowInstance> selectRunningInstanceByBusinessId(@Param("businessId") String businessId);

    /**
     * 按业务id查询最新流程实例
     */
    FlowInstance selectLastInstanceByBusinessId(@Param("businessId") String businessId);

    /**
     * 待办数量
     */
    long countTodo(@Param("flags") List<String> flags);

    /**
     * 已办数量
     */
    long countDone(@Param("userId") String userId);

    /**
     * 我发起的流程数量
     */
    long countMyInstance(@Param("userId") String userId);

    /**
     * 查询超时未审批的任务（create_time 超过指定小时数）
     */
    List<FlowTask> selectTimeoutTasks(@Param("timeoutHours") int timeoutHours);

    /**
     * 查询全部待办任务及其节点扩展配置（按节点 ext 的超时设置逐个判定）
     */
    List<FlowTodoTaskExtDTO> selectTodoTasksWithNodeExt();
}
