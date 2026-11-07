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

package com.privernal.system.service.impl;

import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.system.domain.SystemAppWorkflow;
import com.privernal.system.dto.SystemAppWorkflowDTO;
import com.privernal.system.dto.SystemAppWorkflowDetailDTO;
import com.privernal.system.filter.SystemAppWorkflowFilterDTO;
import com.privernal.system.mapper.SystemAppWorkflowMapper;
import com.privernal.system.service.ISystemAppWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 应用关联的流程单的Service业务层处理
 *
 * @author saperliu
 * @date 2024-07-19
 * @Description 应用关联的流程单的Service业务层处理
 */
@Slf4j
@Service
public class SystemAppWorkflowServiceImpl implements ISystemAppWorkflowService {
    @Autowired
    private SystemAppWorkflowMapper systemAppWorkflowMapper;

    /**
     * 查询应用关联的流程单
     *
     * @param id 应用关联的流程单主键
     * @return 应用关联的流程单
     */
    @Transactional(readOnly = true)
    @Override
    public SystemAppWorkflow selectSystemAppWorkflowById(String id) {
        return systemAppWorkflowMapper.selectById(id);
    }

    /**
     * 查询应用关联的流程单
     *
     * @param id 应用关联的流程单主键
     * @return 应用关联的流程单
     */
    @Transactional(readOnly = true)
    @Override
    public SystemAppWorkflowDetailDTO selectSystemAppWorkflowDetailDTOById(String id) {
        SystemAppWorkflow systemAppWorkflow = systemAppWorkflowMapper.selectById(id);
        SystemAppWorkflowDetailDTO systemAppWorkflowDetailDTO = new SystemAppWorkflowDetailDTO();
        if (systemAppWorkflow != null) {
            BeanUtils.copyProperties(systemAppWorkflow, systemAppWorkflowDetailDTO, DataConstants.IGNORED_ON_COPY);
        }
        return systemAppWorkflowDetailDTO;
    }

    /**
     * 查询应用关联的流程单列表
     *
     * @param systemAppWorkflow 应用关联的流程单
     * @return 应用关联的流程单
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemAppWorkflowDTO> selectSystemAppWorkflowList(SystemAppWorkflowFilterDTO systemAppWorkflow) {
        return systemAppWorkflowMapper.selectSystemAppWorkflowList(systemAppWorkflow);
    }

    /**
     * 新增应用关联的流程单
     *
     * @param systemAppWorkflow 应用关联的流程单
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemAppWorkflow insertSystemAppWorkflow(SystemAppWorkflow systemAppWorkflow) {
        systemAppWorkflowMapper.insert(systemAppWorkflow);
        return systemAppWorkflow;
    }

    /**
     * 修改应用关联的流程单
     *
     * @param systemAppWorkflow 应用关联的流程单
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemAppWorkflow updateSystemAppWorkflow(SystemAppWorkflow systemAppWorkflow) {
        systemAppWorkflowMapper.updateById(systemAppWorkflow);
        return systemAppWorkflow;
    }

    /**
     * 批量删除应用关联的流程单
     *
     * @param ids 需要删除的应用关联的流程单主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemAppWorkflowByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemAppWorkflowById(id);
        }
        return ids.length;
    }

    /**
     * 删除应用关联的流程单信息
     *
     * @param id 应用关联的流程单主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemAppWorkflowById(String id) {
        return systemAppWorkflowMapper.deleteFillById(id);
    }

}
