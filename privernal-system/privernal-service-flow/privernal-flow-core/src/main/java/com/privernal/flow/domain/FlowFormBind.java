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
 * 流程定义与动态表单绑定对象 flow_form_bind
 *
 * @deprecated 绑定关系已并入版本主表 {@link FlowFormVersion}（flow_code + table_code，含版本控制），
 * 旧表仅保留过渡期读取兼容，新配置请使用 /back/flow/form/version 系列接口
 * @author saperliu
 */
@Deprecated
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_form_bind")
public class FlowFormBind extends BaseEntity {

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

    /** 流程定义编码（warm-flow flow_definition.flow_code） */
    @TableField("flow_code")
    private String flowCode;

    /** 流程名称 */
    @TableField("flow_name")
    private String flowName;

    /** 动态表单编码（dynamic_form.table_code） */
    @TableField("table_code")
    private String tableCode;

    /** 表单名称 */
    @TableField("form_name")
    private String formName;

    // ===== 过渡期分页参数（列表端点以 @RequestBody 承载分页；非表字段）=====

    /** 当前页码（非表字段） */
    @TableField(exist = false)
    private Integer pageNum;

    /** 每页条数（非表字段） */
    @TableField(exist = false)
    private Integer pageSize;

    /** 排序字段（非表字段） */
    @TableField(exist = false)
    private String orderBy;

    /** 排序方向 asc/desc（非表字段） */
    @TableField(exist = false)
    private String direction;

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
