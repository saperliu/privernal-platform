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

package com.privernal.system.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemAppWorkflow;
import com.privernal.system.dto.SystemAppWorkflowDTO;
import com.privernal.system.filter.SystemAppWorkflowFilterDTO;

import java.util.List;

/**
 * 应用关联的流程单Mapper接口
 *
 * @author saperliu
 * @date 2024-07-19
 * @Description 应用关联的流程单Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:appWorkflow")
public interface SystemAppWorkflowMapper extends PrivernalMapper<SystemAppWorkflow> {
    /**
     * 查询应用关联的流程单列表
     *
     * @param systemAppWorkflow 应用关联的流程单
     * @return 应用关联的流程单集合
     */
    @PrivernalDataScope(mainAlias = "appWorkflow", permission = "system:appWorkflow:list")
    public List<SystemAppWorkflowDTO> selectSystemAppWorkflowList(SystemAppWorkflowFilterDTO systemAppWorkflow);

}
