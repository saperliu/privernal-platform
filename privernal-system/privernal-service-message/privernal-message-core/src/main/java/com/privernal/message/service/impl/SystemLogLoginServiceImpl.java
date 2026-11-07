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
import com.privernal.message.domain.SystemLogLogin;
import com.privernal.message.dto.SystemLogLoginDTO;
import com.privernal.message.dto.SystemLogLoginDetailDTO;
import com.privernal.message.filter.SystemLogLoginFilterDTO;
import com.privernal.message.mapper.SystemLogLoginMapper;
import com.privernal.message.service.ISystemLogLoginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 登录日志的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 登录日志的Service业务层处理
 */
@Slf4j
@Service
public class SystemLogLoginServiceImpl implements ISystemLogLoginService {
    @Autowired
    private SystemLogLoginMapper systemLogLoginMapper;

    /**
     * 查询登录日志
     *
     * @param id 登录日志主键
     * @return 登录日志
     */
    @Transactional(readOnly = true)
    @Override
    public SystemLogLogin selectSystemLogLoginById(String id) {
        return systemLogLoginMapper.selectById(id);
    }

    /**
     * 查询系统登录日志
     *
     * @param id 系统登录日志主键
     * @return 系统登录日志
     */
    @Transactional(readOnly = true)
    @Override
    public SystemLogLoginDetailDTO selectSystemLogLoginDetailDTOById(String id) {
        SystemLogLogin systemLogLogin = systemLogLoginMapper.selectById(id);
        SystemLogLoginDetailDTO systemLogLoginDetailDTO = new SystemLogLoginDetailDTO();
        if (systemLogLogin != null) {
            BeanUtils.copyProperties(systemLogLogin, systemLogLoginDetailDTO);
        }
        return systemLogLoginDetailDTO;
    }

    /**
     * 查询系统登录日志列表
     *
     * @param systemLogLogin 系统登录日志
     * @return 系统登录日志
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemLogLoginDTO> selectSystemLogLoginList(SystemLogLoginFilterDTO systemLogLogin) {
        return systemLogLoginMapper.selectSystemLogLoginList(systemLogLogin);
    }

    /**
     * 新增登录日志
     *
     * @param systemLogLogin 登录日志
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemLogLogin(SystemLogLogin systemLogLogin) {
        return systemLogLoginMapper.insert(systemLogLogin);
    }

    /**
     * 修改登录日志
     *
     * @param systemLogLogin 登录日志
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemLogLogin(SystemLogLogin systemLogLogin) {
        return systemLogLoginMapper.updateById(systemLogLogin);
    }

    /**
     * 批量删除登录日志
     *
     * @param ids 需要删除的登录日志主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemLogLoginByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemLogLoginById(id);
        }
        return ids.length;
    }

    /**
     * 删除登录日志信息
     *
     * @param id 登录日志主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemLogLoginById(String id) {
        return systemLogLoginMapper.deleteFillById(id);
    }

}
