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

package com.privernal.job.api.action;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 动作执行上下文：动作参数与解析后的业务库名。
 *
 * @author saperliu
 */
@Data
public class TaskExecutionContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 动作参数（任务行 action_config 列解析结果） */
    private Map<String, Object> config;

    /** 业务库名（按组织现场解析优先、注册快照兜底；可能为 null——无库动作可忽略） */
    private String database;

    public TaskExecutionContext(Map<String, Object> config, String database) {
        this.config = config;
        this.database = database;
    }
}
