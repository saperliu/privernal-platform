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

package com.privernal.message.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.message.domain.SystemMessageUser;
import com.privernal.message.dto.SystemMessageUserDTO;
import com.privernal.message.dto.SystemMessageUserDetailDTO;
import com.privernal.message.filter.SystemMessageUserFilterDTO;
import com.privernal.message.mapper.SystemMessageUserMapper;
import com.privernal.message.service.ISystemMessageUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统消息的用户的Service业务层处理
 *
 * @author saperliu
 * @date 2023-05-30
 * @Description 系统消息的用户的Service业务层处理
 */
@Slf4j
@Service
public class SystemMessageUserServiceImpl implements ISystemMessageUserService {
    @Autowired
    private SystemMessageUserMapper systemMessageUserMapper;

    /**
     * 查询系统消息的用户
     *
     * @param id 系统消息的用户主键
     * @return 系统消息的用户
     */
    @Transactional(readOnly = true)
    @Override
    public SystemMessageUser selectSystemMessageUserById(String id) {
        return systemMessageUserMapper.selectById(id);
    }

    /**
     * 查询系统通知人员
     *
     * @param id 系统通知人员主键
     * @return 系统通知人员
     */
    @Transactional(readOnly = true)
    @Override
    public SystemMessageUserDetailDTO selectSystemMessageUserDetailDTOById(String id) {
        SystemMessageUser systemMessageUser = systemMessageUserMapper.selectById(id);
        SystemMessageUserDetailDTO systemMessageUserDetailDTO = new SystemMessageUserDetailDTO();
        if (systemMessageUser != null) {
            BeanUtils.copyProperties(systemMessageUser, systemMessageUserDetailDTO);
        }
        return systemMessageUserDetailDTO;
    }

    /**
     * 查询系统消息的用户列表
     *
     * @param systemMessageUser 系统消息的用户
     * @return 系统消息的用户
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemMessageUserDTO> selectSystemMessageUserList(SystemMessageUserFilterDTO systemMessageUser) {
        return systemMessageUserMapper.selectSystemMessageUserList(systemMessageUser);
    }

    /**
     * 新增系统消息的用户
     *
     * @param systemMessageUser 系统消息的用户
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemMessageUser(SystemMessageUser systemMessageUser) {
        return systemMessageUserMapper.insert(systemMessageUser);
    }

    /**
     * 修改系统消息的用户
     *
     * @param systemMessageUser 系统消息的用户
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemMessageUser(SystemMessageUser systemMessageUser) {
        return systemMessageUserMapper.updateById(systemMessageUser);
    }

    /**
     * 批量删除系统消息的用户
     *
     * @param ids 需要删除的系统消息的用户主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemMessageUserByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemMessageUserById(id);
        }
        return ids.length;
    }

    /**
     * 删除系统消息的用户信息
     *
     * @param id 系统消息的用户主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemMessageUserById(String id) {
        return systemMessageUserMapper.deleteFillById(id);
    }

}
