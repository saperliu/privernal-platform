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

package com.privernal.dynamic.script.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 脚本执行日志（append-only）：每次资产执行落一行（挂点/耗时/结果摘要|异常/data_id），
 * 是"发布前必须试运行成功一次"的判定依据（bind_point=TRIAL 的最近一行 success=Y）。
 */
@Data
@TableName(value = "dynamic_script_exec_log")
public class DynamicScriptExecLog implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String POINT_SAVE_VALIDATE = "SAVE_VALIDATE";
    public static final String POINT_BACK_SCRIPT = "BACK_SCRIPT";
    public static final String POINT_SCHEDULE = "SCHEDULE";
    public static final String POINT_LINKAGE_SCRIPT = "LINKAGE_SCRIPT";
    public static final String POINT_TRIAL = "TRIAL";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String assetId;

    private String scriptCode;

    private Integer version;

    /** SAVE_VALIDATE|BACK_SCRIPT|SCHEDULE|LINKAGE_SCRIPT|TRIAL */
    private String bindPoint;

    private String formId;

    private String tableCode;

    /** 关联数据行（保存校验挂点时为被保存数据 id，试运行为空） */
    private String dataId;

    /** Y/N */
    private String success;

    /** 结果摘要（前 500 字符） */
    private String resultSummary;

    private String errorMessage;

    private Long elapsedMs;

    private String organizationId;

    private Date createdTime;
}
