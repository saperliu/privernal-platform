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
import com.privernal.message.domain.SystemMessageUser;
import com.privernal.message.dto.SystemMessageUserDTO;
import com.privernal.message.filter.SystemMessageUserFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统消息的用户Mapper接口
 *
 * @author saperliu
 * @date 2023-05-30
 * @Description 系统消息的用户Mapper接口
 */
@PrivernalDataScope(permissionMain = "message:systemMessageUser")
public interface SystemMessageUserMapper extends PrivernalMapper<SystemMessageUser> {
    /**
     * 查询系统消息的用户列表
     *
     * @param systemMessageUser 系统消息的用户
     * @return 系统消息的用户集合
     */
    @PrivernalDataScope(mainAlias = "systemMessageUser", permission = "message:systemMessageUser:list")
    public List<SystemMessageUserDTO> selectSystemMessageUserList(SystemMessageUserFilterDTO systemMessageUser);

    /**
     * 根据消息Id,查询用户关联对象
     *
     * @param messageId 消息ID
     * @param userId    用户ID
     * @return
     */
    @PrivernalDataScope(permission = "message:systemMessageUser:list",ignoreDepartment = true)
    public SystemMessageUser selectSystemMessageUserByMessageId(@Param("messageId") String messageId, @Param("userId") String userId);

    /**
     * 更新所有消息为已读
     *
     * @param userId 用户ID
     * @return
     */
    @PrivernalDataScope(permission = "message:systemMessageUser:edit",ignoreDepartment = true)
    public int updateReadAll(@Param("userId") String userId);

}
