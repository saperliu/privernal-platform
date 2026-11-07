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
import com.privernal.dynamic.dba.annotation.Column;
import com.privernal.dynamic.dba.annotation.Table;
import com.privernal.dynamic.dba.constants.MySqlTypeConstant;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Id;
import lombok.Data;

/**
 * form的历史版本对象 dynamic_form_log
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description form的历史版本的实体
 */
@Table(name = "dynamic_form_log")
@TableName(value = "dynamic_form_log", autoResultMap = true)
@Data
public class DynamicFormLog extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * $column.columnComment
     */
    @Id
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Column(name = "id", type = MySqlTypeConstant.VARCHAR, isKey = true, length = 36)
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
    @Column(name = "status", type = MySqlTypeConstant.INT, length = 11)
    private Long status;
    /**
     * $column.columnComment
     */
    @Column(name = "form_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String formId;
    /**
     * $column.columnComment
     */
    @Column(name = "version", type = MySqlTypeConstant.VARCHAR, length = 10)

    private String version;
    /**
     * $column.columnComment
     */

    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "meta_data", type = MySqlTypeConstant.JSON)
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
     * 快照类型；VERSION=正式生效版本（保存/发布/回滚时写入），DRAFT=设计草稿（仅留痕，不生效）
     */
    @Column(name = "log_type", type = MySqlTypeConstant.VARCHAR, length = 20)
    private String logType;

    /** 快照类型：正式生效版本 */
    public static final String LOG_TYPE_VERSION = "VERSION";
    /** 快照类型：设计草稿 */
    public static final String LOG_TYPE_DRAFT = "DRAFT";


}
