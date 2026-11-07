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

import com.privernal.common.core.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.handler.DataFillHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 流程数据填充：任务/历史任务落库前自动继承所属实例的流程标题。
 * 实例标题在启动时按「发起人姓名的流程名」写入（FlowInstanceServiceImpl.start），
 * 因此后续节点创建的任务与办理产生的历史记录标题与实例保持一致。
 */
@Slf4j
@Component
public class FlowDataFillHandler implements DataFillHandler {

    @Autowired
    private FlowTimeoutTaskSyncer timeoutTaskSyncer;

    @Override
    public void insertFill(Object object) {
        // 保留框架默认填充（createTime/createBy 等）
        DataFillHandler.super.insertFill(object);
        try {
            if (object instanceof Task task) {
                fillTaskFlowName(task);
 // 甲方案兜底哨兵：引擎每次插入待办都会走到这里。
                // 只触发"该实例需要重新同步"的异步对齐，不直接注册——插入前主键/上下文
                // 未必就绪，且在引擎事务内注册会拉长事务。即使 §10.2 调用点清单漏了，
                // 任务也只是延后到下一个引擎动作补齐，而不是永久漏注册。
                if (task.getInstanceId() != null) {
                    timeoutTaskSyncer.syncByInstanceAsync(task.getInstanceId());
                }
            } else if (object instanceof HisTask hisTask) {
                if (StringUtils.isEmpty(hisTask.getFlowName())) {
                    hisTask.setFlowName(resolveInstanceFlowName(hisTask.getInstanceId()));
                }
            } else if (object instanceof Instance instance) {
                if (StringUtils.isEmpty(instance.getFlowName())) {
                    Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
                    if (definition != null) {
                        instance.setFlowName(definition.getFlowName());
                    }
                }
            }
        } catch (Exception e) {
            // 填充失败不阻断流程流转
            log.warn("流程标题填充失败：{}", e.getMessage());
        }
    }

    private void fillTaskFlowName(Task task) {
        if (StringUtils.isNotEmpty(task.getFlowName())) {
            return;
        }
        task.setFlowName(resolveInstanceFlowName(task.getInstanceId()));
    }

    private String resolveInstanceFlowName(Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        Instance instance = FlowEngine.insService().getById(instanceId);
        return instance == null ? null : instance.getFlowName();
    }
}
