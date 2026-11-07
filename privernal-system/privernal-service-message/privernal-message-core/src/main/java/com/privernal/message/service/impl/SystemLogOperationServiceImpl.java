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
import com.privernal.message.domain.SystemLogOperation;
import com.privernal.message.dto.SystemLogOperationDTO;
import com.privernal.message.dto.SystemLogOperationDetailDTO;
import com.privernal.message.filter.SystemLogOperationFilterDTO;
import com.privernal.message.mapper.SystemLogOperationMapper;
import com.privernal.message.service.ISystemLogOperationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 操作日志的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 操作日志的Service业务层处理
 */
@Slf4j
@Service
public class SystemLogOperationServiceImpl implements ISystemLogOperationService {
    @Autowired
    private SystemLogOperationMapper systemLogOperationMapper;

    /**
     * 查询操作日志
     *
     * @param id 操作日志主键
     * @return 操作日志
     */
    @Transactional(readOnly = true)
    @Override
    public SystemLogOperation selectSystemLogOperationById(String id) {
        return systemLogOperationMapper.selectById(id);
    }

    /**
     * 查询系统操作日志
     *
     * @param id 系统操作日志主键
     * @return 系统操作日志
     */
    @Transactional(readOnly = true)
    @Override
    public SystemLogOperationDetailDTO selectSystemLogOperationDetailDTOById(String id) {
        SystemLogOperation systemLogOperation = systemLogOperationMapper.selectById(id);
        SystemLogOperationDetailDTO systemLogOperationDetailDTO = new SystemLogOperationDetailDTO();
        if (systemLogOperation != null) {
            BeanUtils.copyProperties(systemLogOperation, systemLogOperationDetailDTO);
        }
        return systemLogOperationDetailDTO;
    }

    /**
     * 查询系统操作日志列表
     *
     * @param systemLogOperation 系统操作日志
     * @return 系统操作日志
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemLogOperationDTO> selectSystemLogOperationList(SystemLogOperationFilterDTO systemLogOperation) {
        return systemLogOperationMapper.selectSystemLogOperationList(systemLogOperation);
    }

    /**
     * 新增操作日志
     *
     * @param systemLogOperation 操作日志
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemLogOperation(SystemLogOperation systemLogOperation) {
        return systemLogOperationMapper.insert(systemLogOperation);
    }

    /**
     * 修改操作日志
     *
     * @param systemLogOperation 操作日志
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemLogOperation(SystemLogOperation systemLogOperation) {
        return systemLogOperationMapper.updateById(systemLogOperation);
    }

    /**
     * 批量删除操作日志
     *
     * @param ids 需要删除的操作日志主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemLogOperationByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemLogOperationById(id);
        }
        return ids.length;
    }

    /**
     * 删除操作日志信息
     *
     * @param id 操作日志主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemLogOperationById(String id) {
        return systemLogOperationMapper.deleteFillById(id);
    }

}
