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

package com.privernal.flow.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程节点表单配置对象 flow_form_node_config
 * 按流程编码+节点编码配置字段/按钮权限，跨定义版本生效（替代旧 wfs 的 7 张表单权限表）
 *
 * @deprecated 已被版本化配置取代：{@link FlowFormVersion} + {@link FlowFormFieldPerm} + {@link FlowFormBtnPerm}
 * （支持草稿/发布/多版本/回滚，字段全集取自动态表单模块），旧表仅保留过渡期读取回退，
 * 新配置请使用 /back/flow/form/version 系列接口
 * @author saperliu
 */
@Deprecated
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_form_node_config")
public class FlowFormNodeConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 组织 */
    @TableField("organization_id")
    private String organizationId;

    /** 部门 */
    @TableField("department_id")
    private String departmentId;

    /** 流程定义id（信息字段，配置实际按 definition_code+node_code 生效） */
    @TableField("definition_id")
    private String definitionId;

    /** 流程定义编码（flow_code） */
    @TableField("definition_code")
    private String definitionCode;

    /** 节点编码 */
    @TableField("node_code")
    private String nodeCode;

    /** 动态表单编码 */
    @TableField("table_code")
    private String tableCode;

    /** 节点字段权限配置JSON：[{"fieldCode":"x","show":true,"edit":true,"required":false,"defaultValue":"","dict":""}] */
    @TableField("field_config")
    private String fieldConfig;

    /** 节点按钮权限配置JSON：[{"btnCode":"approve","name":"通过","show":true,"color":"primary"}] */
    @TableField("btn_config")
    private String btnConfig;
}
