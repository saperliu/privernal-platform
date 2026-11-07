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

package com.privernal.message.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.message.domain.SystemMessage;
import com.privernal.message.dto.MessageUserDTO;
import com.privernal.message.dto.SystemMessageDTO;
import com.privernal.message.filter.SystemMessageFilterDTO;
import com.privernal.message.filter.SystemMessageUserFilterDTO;

import java.util.List;

/**
 * 系统消息Mapper接口
 *
 * @author saperliu
 * @date 2023-05-30
 * @Description 系统消息Mapper接口
 */
@PrivernalDataScope(permissionMain = "message:systemMessage")
public interface SystemMessageMapper extends PrivernalMapper<SystemMessage> {
    /**
     * 查询系统消息列表
     *
     * @param systemMessage 系统消息
     * @return 系统消息集合
     */
    @PrivernalDataScope(mainAlias = "systemMessage", permission = "message:systemMessage:list")
    public List<SystemMessageDTO> selectSystemMessageList(SystemMessageFilterDTO systemMessage);


    /**
     * 查询系统消息列表,APP端使用，查询自己的消息
     *
     * @param systemMessage 系统消息
     * @return 系统消息集合
     */
    @PrivernalDataScope(mainAlias = "systemMessage", permission = "message:systemMessage:list", ignoreDepartment = true)
    public List<MessageUserDTO> selectSystemMessageListByUser(SystemMessageUserFilterDTO systemMessage);

    /**
     * 查询本组织所有未读的信息
     *
     * @return 消息
     */
    @PrivernalDataScope(mainAlias = "systemMessage", permission = "message:systemMessage:list", ignoreDepartment = true)
    public List<SystemMessage> selectSystemMessageListNoRead();


}
