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

/**
 * 动作执行结果。
 *
 * @author saperliu
 */
@Data
public class ActionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行是否成功（失败由内核记 FAILED 并按策略处置） */
    private boolean success;

    /** 结果摘要（落 result_digest，最长 500 字符） */
    private String digest;

    /** 失败原因（落 error_msg） */
    private String error;

    public static ActionResult ok(String digest) {
        ActionResult r = new ActionResult();
        r.success = true;
        r.digest = digest;
        return r;
    }

    public static ActionResult fail(String error) {
        ActionResult r = new ActionResult();
        r.success = false;
        r.error = error;
        return r;
    }
}
