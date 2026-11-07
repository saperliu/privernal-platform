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
 * 流程表单节点按钮权限明细对象 flow_form_btn_perm
 * 按版本+节点维度控制按钮显隐/弹窗/颜色，node_code=global 为全局默认配置
 * （替代旧 wfs 的 workflow_instance_form_btn_task / _task_release）
 *
 * @author saperliu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_form_btn_perm")
public class FlowFormBtnPerm extends BaseEntity {

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

    /** 版本主表id */
    @TableField("version_id")
    private String versionId;

    /** 流程定义编码 */
    @TableField("flow_code")
    private String flowCode;

    /** 版本号 */
    @TableField("version")
    private Integer version;

    /** 节点编码，global 表示全局默认配置 */
    @TableField("node_code")
    private String nodeCode;

    /** 按钮编码（FlowFormBtnEnum） */
    @TableField("btn_code")
    private String btnCode;

    /** 按钮名称 */
    @TableField("btn_name")
    private String btnName;

    /** 按钮别名 */
    @TableField("alias_name")
    private String aliasName;

    /** 是否显示 0否 1是 */
    @TableField("is_show")
    private Integer isShow;

    /** 是否显示确认弹窗 0否 1是 */
    @TableField("show_dialog")
    private Integer showDialog;

    /** 按钮颜色 */
    @TableField("color")
    private String color;

    /** 排序 */
    @TableField("sort")
    private Integer sort;
}
