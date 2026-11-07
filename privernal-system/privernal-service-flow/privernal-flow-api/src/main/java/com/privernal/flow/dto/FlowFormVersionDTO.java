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

package com.privernal.flow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 流程表单配置版本（草稿/发布，对齐旧 wfs 的 _task/_task_release 双表能力并升级为多版本模型）
 *
 * @author saperliu
 */
@Data
public class FlowFormVersionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private String id;

    @Schema(description = "流程定义编码（flow_code）")
    private String flowCode;

    @Schema(description = "流程名称")
    private String flowName;

    @Schema(description = "动态表单编码（dynamic_form.table_code）")
    private String tableCode;

    @Schema(description = "表单名称")
    private String formName;

    @Schema(description = "版本号（同 flowCode 下递增，从1开始）")
    private Integer version;

    @Schema(description = "发布状态 0草稿 1已发布")
    private Integer publishStatus;

    @Schema(description = "是否当前生效发布版 Y/N")
    private String currentFlag;

    @Schema(description = "发布时间")
    private String publishTime;

    @Schema(description = "发布人")
    private String publishBy;

    @Schema(description = "部门ID")
    private String departmentId;

    @Schema(description = "所属组织")
    private String organizationId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "节点字段权限明细")
    private List<FlowFormFieldPermDTO> fieldPermList;

    @Schema(description = "节点按钮权限明细")
    private List<FlowFormBtnPermDTO> btnPermList;
}
