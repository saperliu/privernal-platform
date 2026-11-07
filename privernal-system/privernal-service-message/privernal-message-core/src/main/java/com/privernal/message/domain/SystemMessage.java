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

package com.privernal.message.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统消息对象 system_message
 *
 * @author saperliu
 * @date 2023-05-30
 * @Description 系统消息的实体
 */
@TableName(value = "system_message", autoResultMap = true)
@Data
public class SystemMessage extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 标题
     */
    private String mainTitle;
    /**
     * 二级标题
     */
    private String subTitle;
    /**
     * 类型, WORKFLOW-审批消息，ALARM-报警消息，NOTICE-通知
     */
    private String messageType;
    /**
     * 发送人类型，ALL-所有人[限制组织内]，SEL-选定的人员
     */
    private String messagePerson;
    /**
     * 详情链接
     */
    private String detailUrl;
    /**
     * 内容简介
     */
    private String content;
    /**
     * 状态;（0未读 1已读）
     */

    private Long status;
    /**
     * 组织
     */
    private String organizationId;
    /**
     * 部门
     */
    private String departmentId;

    /**
     * 创建人
     */
    private String createdName;
}
