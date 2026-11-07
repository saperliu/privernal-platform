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

package com.privernal.system.service;

import com.privernal.system.domain.SystemPosition;
import com.privernal.system.dto.SystemUserPositionDTO;
import com.privernal.system.filter.SystemPositionFilterDTO;

import java.util.List;

/**
 * 岗位的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 岗位的Service接口
 */
public interface ISystemPositionService {
    /**
     * 查询岗位
     *
     * @param id 岗位主键
     * @return 岗位
     */
    public SystemPosition selectSystemPositionById(String id);

    /**
     * 查询岗位列表
     *
     * @param systemPosition 岗位
     * @return 岗位集合
     */
    public List<SystemPosition> selectSystemPositionList(SystemPositionFilterDTO systemPosition);

    /**
     * 新增岗位
     *
     * @param systemPosition 岗位
     * @return 结果
     */
    public int insertSystemPosition(SystemPosition systemPosition);

    /**
     * 修改岗位
     *
     * @param systemPosition 岗位
     * @return 结果
     */
    public int updateSystemPosition(SystemPosition systemPosition);

    /**
     * 批量删除岗位
     *
     * @param ids 需要删除的岗位主键集合
     * @return 结果
     */
    public int deleteSystemPositionByIds(String[] ids);

    /**
     * 删除岗位信息
     *
     * @param id 岗位主键
     * @return 结果
     */
    public int deleteSystemPositionById(String id);

    /**
     * 查询岗位里的用户
     *
     * @param positionId 岗位ID
     * @param nickName   用户昵称
     * @return 用户集合
     */
    public List<SystemUserPositionDTO> selectSystemUserByPositionList(String positionId, String nickName);
}
