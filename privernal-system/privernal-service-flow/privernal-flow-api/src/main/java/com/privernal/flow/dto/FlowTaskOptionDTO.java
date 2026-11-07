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
import java.util.List;

/**
 * 任务办理选项（驳回/跳转可选节点、减签可选办理人）
 * 供待办操作弹窗填充下拉数据
 *
 * @author saperliu
 */
@Data
public class FlowTaskOptionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程全部节点（nodeStatus：PENDING 未到达 / ACTIVE 正在办理 / COMPLETED 已流转，供驳回/跳转选择） */
    private List<FlowNodeInfoDTO> nodeList;

    /** 当前任务办理人（减签可选，userId 为 flow_user.processed_by 原值，提交时原样传回） */
    private List<TaskUserOptionDTO> taskUserList;

    /**
     * 任务办理人选项
     */
    @Data
    public static class TaskUserOptionDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** flow_user.processed_by 原值（减签提交原样传回） */
        private String userId;

        /** 展示名（用户昵称，解析失败回退原值） */
        private String userName;
    }
}
