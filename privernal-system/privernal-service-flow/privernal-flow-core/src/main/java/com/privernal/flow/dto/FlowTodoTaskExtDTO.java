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

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 待办任务扩展信息（含节点 ext 配置），超时扫描用
 *
 * @author saperliu
 */
@Data
public class FlowTodoTaskExtDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务id */
    private Long taskId;

    /** 流程定义id */
    private Long definitionId;

    /** 流程实例id */
    private Long instanceId;

    /** 节点编码 */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 任务创建时间（超时起算点） */
    private Date createTime;

    /** 节点扩展配置（JSON 字符串） */
    private String nodeExt;
}
