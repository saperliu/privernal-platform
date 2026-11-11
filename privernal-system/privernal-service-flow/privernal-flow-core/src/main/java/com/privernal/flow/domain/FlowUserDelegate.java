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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.privernal.common.mybatis.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 流程代理规则对象 flow_user_delegate（R2-17 代理与离职移交）
 * <p>
 * user_id 的待办任务生成时由 FlowUserResolveServiceImpl 代理钩子把 agent_id 一并加入
 * 办理人（引擎任务创建回调内生效）；有效期 [start_time, end_time]，status 控制启停。
 * </p>
 *
 * @author saperliu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_user_delegate")
public class FlowUserDelegate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：启用 */
    public static final String STATUS_ENABLED = "1";

    /** 状态：停用 */
    public static final String STATUS_DISABLED = "0";

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 组织 */
    @TableField("organization_id")
    private String organizationId;

    /** 部门 */
    @TableField("department_id")
    private String departmentId;

    /** 被代理人用户id（待办原办理人） */
    @TableField("user_id")
    private String userId;

    /** 代理人用户id（一并加入办理人） */
    @TableField("agent_id")
    private String agentId;

    /** 代理开始时间（空=立即生效） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("start_time")
    private Date startTime;

    /** 代理结束时间（空=长期有效） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("end_time")
    private Date endTime;

    /** 状态（1启用 0停用） */
    @TableField("status")
    private String status;

    /** 说明 */
    @TableField("remark")
    private String remark;
}
