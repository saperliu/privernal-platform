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

import com.privernal.flow.dto.FlowFormVersionDTO;

import java.util.List;

/**
 * 流程表单配置版本服务接口
 * 表单配置的版本控制（草稿/发布/回滚）与节点字段/按钮权限明细维护，
 * 对齐旧 wfs 的 _task/_task_release 双表能力并升级为多版本模型
 *
 * @author saperliu
 */
public interface IFlowFormVersionService {

    /**
     * 查询版本列表
     *
     * @param flowCode      流程编码（可选）
     * @param tableCode     动态表单编码（可选）
     * @param publishStatus 发布状态（可选）
     */
    List<FlowFormVersionDTO> selectVersionList(String flowCode, String tableCode, Integer publishStatus);

    /**
     * 版本详情（含字段/按钮权限明细）
     */
    FlowFormVersionDTO selectVersionDetail(String id);

    /**
     * 初始化草稿：按流程编码+动态表单编码生成第一个草稿版本，
     * 字段全集取自动态表单模块（dynamic_form_column），按钮全集取内置按钮枚举
     */
    boolean initDraft(String flowCode, String tableCode);

    /**
     * 保存草稿（仅草稿版本可保存，字段/按钮明细全量替换）
     */
    boolean saveDraft(FlowFormVersionDTO dto);

    /**
     * 从任一历史版本复制出下一个版本号的草稿
     */
    boolean copyAsDraft(String sourceVersionId);

    /**
     * 发布：草稿 → 已发布并置为当前生效版本（翻转同流程其它版本的 current_flag）
     */
    boolean publish(String id);

    /**
     * 回滚：将历史发布版本重新置为当前生效版本
     */
    boolean rollback(String id);

    /**
     * 删除版本（仅草稿或非当前生效的发布版本可删除）
     */
    boolean deleteVersion(String id);

    /**
     * 按动态表单编码查询当前生效发布版本（含明细），无则返回 null
     */
    FlowFormVersionDTO findCurrentPublishedByTableCode(String tableCode);

    /**
     * 按流程编码查询当前生效发布版本（含明细），无则返回 null
     */
    FlowFormVersionDTO findCurrentPublishedByFlowCode(String flowCode);

    /**
     * 发布该流程下最新草稿版本（流程定义发布时联动，无草稿返回 false）
     */
    boolean publishLatestDraftByFlowCode(String flowCode);

    /**
     * 按流程编码级联删除版本及明细（流程定义删除时联动）
     */
    boolean deleteByFlowCode(String flowCode);

    /**
     * 旧表数据迁移：flow_form_bind + flow_form_node_config → flow_form_version 发布版 v1
     *
     * @return 迁移的绑定数量
     */
    int migrateFromLegacy();
}
