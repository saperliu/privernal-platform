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
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程实例评论对象 flow_task_comment（R2-17 评论与附件）。
 * <p>
 * 附件不落独立表：上传复用 /back/file/upload（module=flow-comment），返回的
 * 文件信息（fileId/name/url）以 JSON 数组文本存 attachments，评论列表原样回显。
 * </p>
 *
 * @author saperliu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_task_comment")
public class FlowTaskComment extends BaseEntity {

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

    /** 流程实例id（flow_instance.id） */
    @TableField("instance_id")
    private String instanceId;

    /** 流程任务id（可空：实例级评论不带任务） */
    @TableField("task_id")
    private String taskId;

    /** 评论人用户id */
    @TableField("user_id")
    private String userId;

    /** 评论人姓名 */
    @TableField("user_name")
    private String userName;

    /** 评论内容 */
    @TableField("content")
    private String content;

    /** 附件 JSON 数组（[{fileId,name,url}]） */
    @TableField("attachments")
    private String attachments;
}
