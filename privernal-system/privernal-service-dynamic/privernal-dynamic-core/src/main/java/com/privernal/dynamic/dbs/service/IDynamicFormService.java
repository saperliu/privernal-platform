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

package com.privernal.dynamic.dbs.service;

import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormLog;
import com.privernal.dynamic.dto.DynamicFormDTO;
import com.privernal.dynamic.dto.DynamicFormDetailDTO;
import com.privernal.dynamic.filter.DynamicFormFilterDTO;

import java.util.List;

/**
 * 前端form配置的Service接口
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description 前端form配置的Service接口
 */
public interface IDynamicFormService {
    /**
     * 查询前端form配置
     *
     * @param id 前端form配置主键
     * @return 前端form配置
     */
    public DynamicForm selectDynamicFormById(String id);

    /**
     * 查询前端form配置
     *
     * @param id 前端form配置主键
     * @return 前端form配置
     */
    public DynamicFormDetailDTO selectDynamicFormDetailDTOById(String id);

    /**
     * 查询前端form配置列表
     *
     * @param dynamicForm 前端form配置
     * @return 前端form配置集合
     */
    public List<DynamicFormDTO> selectDynamicFormList(DynamicFormFilterDTO dynamicForm);

    /**
     * 新增前端form配置
     *
     * @param dynamicForm 前端form配置
     * @return 结果
     */
    public DynamicForm insertDynamicForm(DynamicForm dynamicForm);

    /**
     * 修改前端form配置
     *
     * @param dynamicForm 前端form配置
     * @return 结果
     */
    public DynamicForm updateDynamicForm(DynamicForm dynamicForm);

    /**
     * 批量删除前端form配置
     *
     * @param ids 需要删除的前端form配置主键集合
     * @return 结果
     */
    public int deleteDynamicFormByIds(String[] ids);

    /**
     * 删除前端form配置信息
     *
     * @param id 前端form配置主键
     * @return 结果
     */
    public int deleteDynamicFormById(String id);

    /**
     * 查询前端form配置
     *
     * @param tableCode 前端form配置主键
     * @return 前端form配置
     */
    public DynamicForm selectDynamicFormByTableCode(String tableCode);

    /**
     * 保存表单设计草稿：只写 DRAFT 快照留痕，不改 dynamic_form.meta_data、不 ALTER 物理表、不动关联登记，
     * 即草稿不生效；同一表单仅保留最新一条未发布草稿
     *
     * @param dynamicForm 草稿内容（id 必传，metaData/title/isWorkflow/workflowCode 取提交值）
     * @return 草稿快照
     */
    public DynamicFormLog saveFormDraft(DynamicForm dynamicForm);

    /**
     * 查询表单最新的未发布设计草稿
     *
     * @param formId 表单主键
     * @return 最新草稿快照；无草稿返回 null
     */
    public DynamicFormLog findFormDraft(String formId);

    /**
     * 发布指定快照：把快照内容（metaData/title/isWorkflow/workflowCode）应用为生效定义——
     * 全量覆盖生效字段、重建关联登记、同步物理表结构（含子表）、写入新 VERSION 快照；
     * 草稿快照发布后即被清除
     *
     * @param logId 快照主键（dynamic_form_log.id）
     * @return 生效后的表单
     */
    public DynamicForm publishFormLog(String logId);

    /**
     * 回滚到指定正式版本：与发布同一条生效链，把历史 VERSION 快照重新应用；版本号继续递增
     *
     * @param logId 快照主键（dynamic_form_log.id，须为 VERSION 类型）
     * @return 回滚后的表单
     */
    public DynamicForm rollbackFormLog(String logId);
}
