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

package com.privernal.dynamic.dto;

import com.privernal.common.web.domain.BaseDTO;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * 前端form配置对象 dynamic_form
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description 前端form配置对象 dynamic_form
 */
@Tag(name = "DynamicFormDTO", description = "前端form配置对应的DTO")
@Data
public class DynamicFormDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 所属部门
     */
    @Schema(description = "所属部门")

    private String departmentId;

    /**
     * 所属组织
     */
    @Schema(description = "所属组织")

    private String organizationId;

    /**
     * 数据标题
     */
    @Schema(description = "数据标题")

    private String title;

    /**
     * 状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消
     */
    @Schema(description = "状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消")

    private Long status;

    /**
     * 表单编码,数据库表的表名
     */
    @Schema(description = "表单编码,数据库表的表名")

    private String tableCode;

    /**
     * 表单名称
     */
    @Schema(description = "表单名称")

    private String tableName;

    /**
     * 配置的前端界面的json
     */
    @Schema(description = "配置的前端界面的json")

    private JSONObject metaData;

    /**
     * 是否启用工作流 0 未启用  1 启用
     */
    @Schema(description = "是否启用工作流 0 未启用  1 启用")

    private Long isWorkflow;

    /**
     * 工作流的标识
     */
    @Schema(description = "工作流的标识")

    private String workflowCode;

    /**
     * 所属应用
     */
    @Schema(description = "所属应用")
    private String appId;
}
