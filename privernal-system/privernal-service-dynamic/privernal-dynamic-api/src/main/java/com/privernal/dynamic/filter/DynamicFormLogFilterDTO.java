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

package com.privernal.dynamic.filter;

import com.privernal.common.web.domain.FilterDTO;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * form的历史版本对象 dynamic_form_log
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description form的历史版本的过滤器
 */
@Tag(name = "DynamicFormLogFilterDTO", description = "form的历史版本对应的查询过滤器")
@Data
public class DynamicFormLogFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


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
     * $column.columnComment
     */
    @Schema(description = "$column.columnComment")
    private String formId;


    /**
     * $column.columnComment
     */
    @Schema(description = "$column.columnComment")
    private String version;


    /**
     * $column.columnComment
     */
    @Schema(description = "$column.columnComment")
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
     * 快照类型；VERSION=正式生效版本，DRAFT=设计草稿（不生效）
     */
    @Schema(description = "快照类型；VERSION=正式生效版本，DRAFT=设计草稿（不生效）")
    private String logType;


}
