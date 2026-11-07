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

import com.privernal.flow.filter.FlowDefinitionFilterDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.Definition;

/**
 * 流程定义服务接口
 *
 * @author saperliu
 */
public interface IFlowDefinitionService {

    /**
     * 定义分页
     */
    Page<Definition> pageDefinitions(FlowDefinitionFilterDTO filter);

    /**
     * 定义详情（含节点与跳转）
     */
    Definition getDefinition(Long id);

    /**
     * 保存设计器 JSON（新增或修改）
     */
    Definition saveDefJson(DefJson defJson);

    /**
     * 发布
     */
    boolean publish(Long id);

    /**
     * 停用（取消发布，已有实例不受影响）
     */
    boolean unPublish(Long id);

    /**
     * 激活
     */
    boolean active(Long id);

    /**
     * 挂起
     */
    boolean unActive(Long id);

    /**
     * 复制定义
     */
    boolean copyDef(Long id);

    /**
     * 删除定义（联动删除表单绑定与节点配置）
     */
    boolean removeDef(String[] ids);

    /**
     * 按流程编码删除全部版本的流程（联动清理运行实例、待办/已办任务、表单绑定与节点配置）。
     * 动态表单删除时调用：表单连数据一起删了，挂在它上面的流程没有独立存在的意义。
     *
     * @param flowCode 流程编码（动态表单的 workflowCode）
     * @return 删除的定义数量（无该编码的定义返回 0）
     */
    int removeByFlowCode(String flowCode);

    /**
     * 导出定义 JSON
     */
    String exportJson(Long id);

    /**
     * 组织同步初始化（将平台模板的表单绑定与节点权限配置复制到目标组织，已存在则跳过）
     *
     * @param organizationId 目标组织
     * @param departmentId   目标部门（为空时取组织ID）
     * @return 实际新增的绑定数
     */
    int initForOrganization(String organizationId, String departmentId);
}
