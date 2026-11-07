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

import com.privernal.dynamic.dbs.domain.DynamicFormLog;
import com.privernal.dynamic.dto.DynamicFormLogDTO;
import com.privernal.dynamic.dto.DynamicFormLogDetailDTO;
import com.privernal.dynamic.filter.DynamicFormLogFilterDTO;

import java.util.List;

/**
 * form的历史版本的Service接口
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description form的历史版本的Service接口
 */
public interface IDynamicFormLogService {
    /**
     * 查询form的历史版本
     *
     * @param id form的历史版本主键
     * @return form的历史版本
     */
    public DynamicFormLog selectDynamicFormLogById(String id);

    /**
     * 查询form的历史版本
     *
     * @param id form的历史版本主键
     * @return form的历史版本
     */
    public DynamicFormLogDetailDTO selectDynamicFormLogDetailDTOById(String id);

    /**
     * 查询form的历史版本列表
     *
     * @param dynamicFormLog form的历史版本
     * @return form的历史版本集合
     */
    public List<DynamicFormLogDTO> selectDynamicFormLogList(DynamicFormLogFilterDTO dynamicFormLog);

    /**
     * 新增form的历史版本
     *
     * @param dynamicFormLog form的历史版本
     * @return 结果
     */
    public DynamicFormLog insertDynamicFormLog(DynamicFormLog dynamicFormLog);

    /**
     * 修改form的历史版本
     *
     * @param dynamicFormLog form的历史版本
     * @return 结果
     */
    public DynamicFormLog updateDynamicFormLog(DynamicFormLog dynamicFormLog);

    /**
     * 批量删除form的历史版本
     *
     * @param ids 需要删除的form的历史版本主键集合
     * @return 结果
     */
    public int deleteDynamicFormLogByIds(String[] ids);

    /**
     * 删除form的历史版本信息
     *
     * @param id form的历史版本主键
     * @return 结果
     */
    public int deleteDynamicFormLogById(String id);

}
