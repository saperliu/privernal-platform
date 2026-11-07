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

import com.privernal.system.domain.SystemUserPosition;
import com.privernal.system.dto.SystemUserPositionDTO;
import com.privernal.system.filter.SystemUserPositionFilterDTO;

import java.util.List;

/**
 * 用户的岗位的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户的岗位的Service接口
 */
public interface ISystemUserPositionService {
    /**
     * 查询用户的岗位
     *
     * @param id 用户的岗位主键
     * @return 用户的岗位
     */
    public SystemUserPosition selectSystemUserPositionById(String id);

    /**
     * 查询用户的岗位列表
     *
     * @param systemUserPosition 用户的岗位
     * @return 用户的岗位集合
     */
    public List<SystemUserPosition> selectSystemUserPositionList(SystemUserPositionFilterDTO systemUserPosition);

    /**
     * 新增用户的岗位
     *
     * @param systemUserPosition 用户的岗位
     * @return 结果
     */
    public int insertSystemUserPosition(SystemUserPosition systemUserPosition);

    /**
     * 修改用户的岗位
     *
     * @param systemUserPosition 用户的岗位
     * @return 结果
     */
    public int updateSystemUserPosition(SystemUserPosition systemUserPosition);

    /**
     * 批量删除用户的岗位
     *
     * @param ids 需要删除的用户的岗位主键集合
     * @return 结果
     */
    public int deleteSystemUserPositionByIds(String[] ids);

    /**
     * 删除用户的岗位信息
     *
     * @param id 用户的岗位主键
     * @return 结果
     */
    public int deleteSystemUserPositionById(String id);

    /**
     * 新增岗位里的用户
     *
     * @param systemUserPositionDTO 岗位里的用户
     * @return 结果
     */
    public int insertBatchSystemUserPosition(SystemUserPositionDTO systemUserPositionDTO);
}
