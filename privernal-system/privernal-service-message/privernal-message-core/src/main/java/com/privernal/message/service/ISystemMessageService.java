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

import com.privernal.message.domain.SystemMessage;
import com.privernal.message.dto.MessageUserDTO;
import com.privernal.message.dto.SystemMessageDTO;
import com.privernal.message.dto.SystemMessageDetailDTO;
import com.privernal.message.filter.SystemMessageFilterDTO;
import com.privernal.message.filter.SystemMessageUserFilterDTO;

import java.util.List;

/**
 * 系统消息的Service接口
 *
 * @author saperliu
 * @date 2023-05-30
 * @Description 系统消息的Service接口
 */
public interface ISystemMessageService {
    /**
     * 查询系统消息
     *
     * @param id 系统消息主键
     * @return 系统消息
     */
    public SystemMessage selectSystemMessageById(String id);

    /**
     * 查询全员通知（messagePerson=ALL，按创建时间倒序取最近 20 条），首页公告栏使用
     *
     * @return 全员通知集合
     */
    public List<SystemMessage> selectBulletinMessageList();

    /**
     * 查询系统消息通知
     *
     * @param id 系统消息通知主键
     * @return 系统消息通知
     */
    public SystemMessageDetailDTO selectSystemMessageDetailDTOById(String id);

    /**
     * 查询系统消息通知列表
     *
     * @param systemMessage 系统消息通知
     * @return 系统消息通知集合
     */
    public List<SystemMessageDTO> selectSystemMessageList(SystemMessageFilterDTO systemMessage);

    /**
     * 新增系统消息
     *
     * @param systemMessage 系统消息
     * @return 结果
     */
    public int insertSystemMessage(SystemMessage systemMessage);

    /**
     * 修改系统消息
     *
     * @param systemMessage 系统消息
     * @return 结果
     */
    public int updateSystemMessage(SystemMessage systemMessage);

    /**
     * 批量删除系统消息
     *
     * @param ids 需要删除的系统消息主键集合
     * @return 结果
     */
    public int deleteSystemMessageByIds(String[] ids);

    /**
     * 删除系统消息信息
     *
     * @param id 系统消息主键
     * @return 结果
     */
    public int deleteSystemMessageById(String id);

    /**
     * 查询系统消息列表
     *
     * @param userFilterDTO 系统消息
     * @return 系统消息集合
     */
    public List<MessageUserDTO> selectUserMessageList(SystemMessageUserFilterDTO userFilterDTO);

    /**
     * 系统消息标记已读
     *
     * @param messageId 系统消息ID
     * @param userId    用户ID
     * @param nickName  用户昵称
     * @return 系统消息集合
     */
    public int saveUserMessageRead(String messageId, String userId, String nickName);

    /**
     * 系统消息标记全读
     *
     * @param userId   用户ID
     * @param nickName 用户昵称
     * @return 系统消息集合
     */
    public int saveUserMessageAllRead(String userId, String nickName);

    /**
     * 新增系统消息
     *
     * @param systemMessageDTO 系统消息
     * @return 结果
     */
    public SystemMessageDTO saveSystemMessage(SystemMessageDTO systemMessageDTO);
}
