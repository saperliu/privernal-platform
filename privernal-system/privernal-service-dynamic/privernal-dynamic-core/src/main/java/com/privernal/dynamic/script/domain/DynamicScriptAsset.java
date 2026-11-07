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
 * 脚本资产：在线业务算法资产，写一次、多表单复用、
 * 被保存链路事务性调用。执行一律走 Groovy 沙箱（{@link com.privernal.dynamic.dbs.groovy.GroovySandboxConfig}）。
 * <p>
 * 版本语义对齐流程定义：DRAFT 带 id 覆盖同版本；PUBLISHED 改 body 或签名（删参/改类型）必须
 * 递增版本并回到 DRAFT，重新发布前必须试运行成功一次。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dynamic_script_asset", autoResultMap = true)
public class DynamicScriptAsset extends BaseEntity {
    private static final long serialVersionUID = 1L;

    public static final String CATEGORY_VALIDATION = "VALIDATION";
    public static final String CATEGORY_CALC = "CALC";
    public static final String CATEGORY_AUTOFILL = "AUTOFILL";
    public static final String CATEGORY_DATA_OP = "DATA_OP";

    public static final String LANGUAGE_GROOVY = "GROOVY";

    public static final String RESULT_NONE = "NONE";
    public static final String RESULT_BOOLEAN = "BOOLEAN";
    public static final String RESULT_OBJECT = "OBJECT";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DISABLED = "DISABLED";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 资产编码，组织内唯一（uk：script_code + organization_id） */
    private String scriptCode;

    private String scriptName;

    /** VALIDATION|CALC|AUTOFILL|DATA_OP */
    private String category;

    /** v1 唯一枚举 GROOVY */
    private String language;

    /** 脚本正文（沙箱白名单校验在保存/发布前强制） */
    private String body;

    /**
     * 参数契约 JSON：[{"name","type"(STRING|NUMBER|DATE|BOOLEAN|JSON),"required","default","description"}]
     * scriptCode + params_schema 是资产的稳定接口
     */
    private String paramsSchema;

    /** NONE|BOOLEAN|OBJECT */
    private String resultType;

    /** 执行超时（秒），默认 10，上限 60 */
    private Integer timeoutSeconds;

    /** DRAFT|PUBLISHED|DISABLED */
    private String status;

    private Integer version;

    private String appId;

    private String organizationId;

    private String departmentId;
}
