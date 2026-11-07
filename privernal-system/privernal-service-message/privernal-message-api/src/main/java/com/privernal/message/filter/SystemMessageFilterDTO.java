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

package com.privernal.message.filter;

import com.privernal.common.web.domain.FilterDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * 系统消息对象 system_message
 *
 * @author saperliu
 * @date 2023-05-30
 * @Description 系统消息的过滤器
 */
@Tag(name = "SystemMessageFilterDTO", description = "系统消息对应的查询过滤器")
@Data
public class SystemMessageFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 标题
     */
    @Schema(description = "标题")
    private String mainTitle;


    /**
     * 二级标题
     */
    @Schema(description = "二级标题")
    private String subTitle;


    /**
     * 类型, WORKFLOW-审批消息，ALARM-报警消息，NOTICE-通知
     */
    @Schema(description = "类型, WORKFLOW-审批消息，ALARM-报警消息，NOTICE-通知")
    private String messageType;


    /**
     * 发送人类型，ALL-所有人[限制组织内]，SEL-选定的人员
     */
    @Schema(description = "发送人类型，ALL-所有人[限制组织内]，SEL-选定的人员")
    private String messagePerson;


    /**
     * 详情链接
     */
    @Schema(description = "详情链接")
    private String detailUrl;


    /**
     * 内容简介
     */
    @Schema(description = "内容简介")
    private String content;


    /**
     * 状态;（0未读 1已读）
     */
    @Schema(description = "状态;（0未读 1已读）")
    private Long status;


    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;


    /**
     * 组织
     */
    @Schema(description = "组织")
    private String organizationId;


    /**
     * 部门
     */
    @Schema(description = "部门")
    private String departmentId;

    /**
     * 创建人
     */
    private String createdName;


}
