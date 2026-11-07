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

/**
 * 流程表单节点按钮权限明细（对齐旧 wfs 的 workflow_instance_form_btn_task）
 *
 * @author saperliu
 */
@Data
public class FlowFormBtnPermDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private String id;

    @Schema(description = "版本主表id")
    private String versionId;

    @Schema(description = "流程定义编码")
    private String flowCode;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "节点编码，global 表示全局默认配置")
    private String nodeCode;

    @Schema(description = "按钮编码（FlowFormBtnEnum）")
    private String btnCode;

    @Schema(description = "按钮名称")
    private String btnName;

    @Schema(description = "按钮别名")
    private String aliasName;

    @Schema(description = "是否显示 0否 1是")
    private Integer isShow;

    @Schema(description = "是否显示确认弹窗 0否 1是")
    private Integer showDialog;

    @Schema(description = "按钮颜色")
    private String color;

    @Schema(description = "排序")
    private Integer sort;
}
