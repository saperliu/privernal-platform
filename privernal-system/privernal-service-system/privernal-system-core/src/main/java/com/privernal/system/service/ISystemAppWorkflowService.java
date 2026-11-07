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

import com.privernal.system.domain.SystemAppWorkflow;
import com.privernal.system.dto.SystemAppWorkflowDTO;
import com.privernal.system.dto.SystemAppWorkflowDetailDTO;
import com.privernal.system.filter.SystemAppWorkflowFilterDTO;

import java.util.List;

/**
 * 应用关联的流程单的Service接口
 *
 * @author saperliu
 * @date 2024-07-19
 * @Description 应用关联的流程单的Service接口
 */
public interface ISystemAppWorkflowService {
    /**
     * 查询应用关联的流程单
     *
     * @param id 应用关联的流程单主键
     * @return 应用关联的流程单
     */
    public SystemAppWorkflow selectSystemAppWorkflowById(String id);

    /**
     * 查询应用关联的流程单
     *
     * @param id 应用关联的流程单主键
     * @return 应用关联的流程单
     */
    public SystemAppWorkflowDetailDTO selectSystemAppWorkflowDetailDTOById(String id);

    /**
     * 查询应用关联的流程单列表
     *
     * @param systemAppWorkflow 应用关联的流程单
     * @return 应用关联的流程单集合
     */
    public List<SystemAppWorkflowDTO> selectSystemAppWorkflowList(SystemAppWorkflowFilterDTO systemAppWorkflow);

    /**
     * 新增应用关联的流程单
     *
     * @param systemAppWorkflow 应用关联的流程单
     * @return 结果
     */
    public SystemAppWorkflow insertSystemAppWorkflow(SystemAppWorkflow systemAppWorkflow);

    /**
     * 修改应用关联的流程单
     *
     * @param systemAppWorkflow 应用关联的流程单
     * @return 结果
     */
    public SystemAppWorkflow updateSystemAppWorkflow(SystemAppWorkflow systemAppWorkflow);

    /**
     * 批量删除应用关联的流程单
     *
     * @param ids 需要删除的应用关联的流程单主键集合
     * @return 结果
     */
    public int deleteSystemAppWorkflowByIds(String[] ids);

    /**
     * 删除应用关联的流程单信息
     *
     * @param id 应用关联的流程单主键
     * @return 结果
     */
    public int deleteSystemAppWorkflowById(String id);

}
