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

package com.privernal.job.api;

import lombok.Data;

import java.io.Serializable;

/**
 * 注册结果引用。
 *
 * @author saperliu
 */
@Data
public class TaskRef implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 id */
    private String taskId;

    /** 业务键（回传调用方入参，便于核对） */
    private String bizKey;

    /** 是否为"命中已有行的幂等 upsert"（true=改期/复活，false=新建） */
    private boolean upserted;

    /** 是否延迟生效：目标任务正处于 TRIGGERED/RUNNING，本次未改行，调用方应在下一个业务事件重试（§7.5） */
    private boolean deferred;

    public static TaskRef of(String taskId, String bizKey, boolean upserted, boolean deferred) {
        TaskRef ref = new TaskRef();
        ref.taskId = taskId;
        ref.bizKey = bizKey;
        ref.upserted = upserted;
        ref.deferred = deferred;
        return ref;
    }
}
