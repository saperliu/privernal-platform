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

import com.privernal.dynamic.dbs.domain.DynamicDatabase;
import com.privernal.dynamic.dto.DynamicDatabaseDTO;
import com.privernal.dynamic.dto.DynamicDatabaseDetailDTO;
import com.privernal.dynamic.filter.DynamicDatabaseFilterDTO;

import java.util.List;

/**
 * 动态数据库的Service接口
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description 动态数据库的Service接口
 */
public interface IDynamicDatabaseService {
    /**
     * 查询动态数据库
     *
     * @param id 动态数据库主键
     * @return 动态数据库
     */
    public DynamicDatabase selectDynamicDatabaseById(String id);

    /**
     * 查询动态数据库
     *
     * @param id 动态数据库主键
     * @return 动态数据库
     */
    public DynamicDatabaseDetailDTO selectDynamicDatabaseDetailDTOById(String id);

    /**
     * 查询动态数据库列表
     *
     * @param dynamicDatabase 动态数据库
     * @return 动态数据库集合
     */
    public List<DynamicDatabaseDTO> selectDynamicDatabaseList(DynamicDatabaseFilterDTO dynamicDatabase);

    /**
     * 新增动态数据库
     *
     * @param dynamicDatabase 动态数据库
     * @return 结果
     */
    public DynamicDatabase insertDynamicDatabase(DynamicDatabase dynamicDatabase);

    /**
     * 修改动态数据库
     *
     * @param dynamicDatabase 动态数据库
     * @return 结果
     */
    public DynamicDatabase updateDynamicDatabase(DynamicDatabase dynamicDatabase);

    /**
     * 批量删除动态数据库
     *
     * @param ids 需要删除的动态数据库主键集合
     * @return 结果
     */
    public int deleteDynamicDatabaseByIds(String[] ids);

    /**
     * 删除动态数据库信息
     *
     * @param id 动态数据库主键
     * @return 结果
     */
    public int deleteDynamicDatabaseById(String id);

    /**
     * 查询组织登记的业务库
     *
     * @param organizationId 组织
     * @return 动态数据库配置，未登记时返回空对象
     */
    public DynamicDatabaseDTO selectDynamicDatabase(String organizationId);

}
