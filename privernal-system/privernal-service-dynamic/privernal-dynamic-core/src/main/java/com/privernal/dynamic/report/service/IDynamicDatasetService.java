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

package com.privernal.dynamic.report.service;

import com.privernal.dynamic.report.domain.DynamicDataset;

import java.util.List;

/**
 * 报表数据集服务
 *
 * @author saperliu
 */
public interface IDynamicDatasetService {

    /**
     * 保存数据集（新增/按编码更新）：校验表单存在后，从 dynamic_form_column 解析列快照落库
     */
    DynamicDataset saveDynamicDataset(DynamicDataset dataset);

    /**
     * 构建不落库的临时数据集（B8 Chat BI 问数）：按逻辑表码解析表单并内存构建列快照，
     * datasetCode 为运行期标识（TRANSIENT: 前缀），禁止写入任何表。
     * 数据集保存链的非外部源分支与本方法走同一条解析路径。
     */
    DynamicDataset buildTransientDataset(String tableCode);

    /**
     * 构建不落库的临时数据集（B8-M3-2 跨表关联问数）：joins 为白名单关联意图
     * [{type:'subTable'|'relation', field, tableCode?}]，服务端按主表元数据与
     * dynamic_form_relation 登记关系重解析（模型不得自创 JOIN，未登记给可读 400），
     * 关联表列以「{field}_{列}」前缀并入列快照，快照带 joins 段供查询层拼 JOIN。
     */
    DynamicDataset buildTransientDataset(String tableCode, com.alibaba.fastjson2.JSONArray joins);

    /**
     * 刷新列快照：按底层表单当前列重建快照；已保存图表引用了被删除字段时报错，不做静默兼容
     */
    DynamicDataset refreshDataset(String datasetCode);

    /**
     * 按编码查询
     */
    DynamicDataset findByCode(String datasetCode);

    /**
     * 按应用查询列表
     */
    List<DynamicDataset> listByApp(String appId);

    /**
     * 删除（被图表引用时拒绝）
     */
    int deleteByCode(String datasetCode);
}
