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

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 脚本资产发布快照（复用 form_log 模式，logType=SCRIPT 同族口径）。
 * 发布时整行落快照：绑定钉住 (asset_id, version) 后，即使资产继续演进，
 * 历史版本的 body 仍可从此表回溯供执行器解析。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dynamic_script_asset_log")
public class DynamicScriptAssetLog extends BaseEntity {
    private static final long serialVersionUID = 1L;

    public static final String LOG_PUBLISH = "PUBLISH";
    public static final String LOG_DISABLE = "DISABLE";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String assetId;

    private String scriptCode;

    private Integer version;

    /** PUBLISH|DISABLE */
    private String logType;

    /** 发布时的整行快照 JSON（含 body 与 params_schema） */
    private String snapshot;

    private String organizationId;
}
