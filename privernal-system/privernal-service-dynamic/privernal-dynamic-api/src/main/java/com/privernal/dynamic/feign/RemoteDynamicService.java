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

package com.privernal.dynamic.feign;

import com.privernal.common.core.constant.Constants;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.dto.DynamicDatabaseDTO;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * @author saperliu
 * @date 2022-04-10
 * @Description 工作流服务
 */
public interface RemoteDynamicService {

    /**
     * 根据表单编码查询表单的列的信息
     *
     * @param tableCode 表单编码
     * @return 列的集合
     */
    @Operation(summary = "根据tableCode查询一条form数据的列信息")
    @GetMapping("/inner/dbs/open/table/column/{tableCode}")
    public CommonResult<List<DynamicFormColumnDTO>> findTableColumnByCode(@PathVariable String tableCode, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 查询组织的动态数据库
     *
     * @param organizationId 组织
     * @return 数据库配置
     */
    public DynamicDatabaseDTO findDynamicDatabaseByCode(String organizationId);

    /**
     * 回写业务数据的工作流状态列（status：0草稿 1已完成 2运行中 3取消）
     * 供工作流模块（privernal-flow）在流程状态变化时调用
     *
     * @param tableCode 动态表单编码
     * @param dataId    业务数据id
     * @param status    工作流数据状态
     * @return 是否成功
     */
    public boolean updateBusinessStatus(String tableCode, String dataId, Integer status);

    /**
     * 按流程编码反查绑定该流程的动态表单编码（dynamic_form.workflow_code）
     * 供工作流在 flow_form_bind / flow_form_version 都未配置时定位业务表，回写数据状态
     *
     * @param flowCode 流程编码
     * @return 表单编码，未绑定返回 null
     */
    public String findTableCodeByFlowCode(String flowCode);
}
