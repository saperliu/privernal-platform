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

package com.privernal.dynamic.dbs.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.privernal.common.mybatis.type.JSONObjectTypeHandler;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 前端form配置对象 dynamic_form
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description 前端form配置的实体
 */
@TableName(value = "dynamic_form", autoResultMap = true)
@Data
public class DynamicForm extends BaseEntity {


    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 所属部门
     */
    private String departmentId;
    /**
     * 所属组织
     */
    private String organizationId;
    /**
     * 数据标题
     */
    private String title;
    /**
     * 状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消
     */
    private Long status;
    /**
     * 表单编码,数据库表的表名
     */
    private String tableCode;
    /**
     * 表单名称
     */
    private String tableName;
    /**
     * 配置的前端界面的json
     */
    @TableField(typeHandler = JSONObjectTypeHandler.class)
    private JSONObject metaData;
    /**
     * 是否启用工作流 0 未启用  1 启用
     */
    private Long isWorkflow;
    /**
     * 工作流的标识
     */
    private String workflowCode;

    /**
     * 所属应用
     */
    private String appId;
}
