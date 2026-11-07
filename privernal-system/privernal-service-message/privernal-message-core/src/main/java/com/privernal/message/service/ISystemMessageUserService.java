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

package com.privernal.message.service;

import com.privernal.message.domain.SystemMessageUser;
import com.privernal.message.dto.SystemMessageUserDTO;
import com.privernal.message.dto.SystemMessageUserDetailDTO;
import com.privernal.message.filter.SystemMessageUserFilterDTO;

import java.util.List;

/**
 * 系统消息的用户的Service接口
 *
 * @author saperliu
 * @date 2023-05-30
 * @Description 系统消息的用户的Service接口
 */
public interface ISystemMessageUserService {
    /**
     * 查询系统消息的用户
     *
     * @param id 系统消息的用户主键
     * @return 系统消息的用户
     */
    public SystemMessageUser selectSystemMessageUserById(String id);

    /**
     * 查询系统通知人员
     *
     * @param id 系统通知人员主键
     * @return 系统通知人员
     */
    public SystemMessageUserDetailDTO selectSystemMessageUserDetailDTOById(String id);

    /**
     * 查询系统消息的用户列表
     *
     * @param systemMessageUser 系统消息的用户
     * @return 系统消息的用户集合
     */
    public List<SystemMessageUserDTO> selectSystemMessageUserList(SystemMessageUserFilterDTO systemMessageUser);

    /**
     * 新增系统消息的用户
     *
     * @param systemMessageUser 系统消息的用户
     * @return 结果
     */
    public int insertSystemMessageUser(SystemMessageUser systemMessageUser);

    /**
     * 修改系统消息的用户
     *
     * @param systemMessageUser 系统消息的用户
     * @return 结果
     */
    public int updateSystemMessageUser(SystemMessageUser systemMessageUser);

    /**
     * 批量删除系统消息的用户
     *
     * @param ids 需要删除的系统消息的用户主键集合
     * @return 结果
     */
    public int deleteSystemMessageUserByIds(String[] ids);

    /**
     * 删除系统消息的用户信息
     *
     * @param id 系统消息的用户主键
     * @return 结果
     */
    public int deleteSystemMessageUserById(String id);

}
