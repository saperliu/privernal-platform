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

package com.privernal.system.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * OpenAPI 调用流水 system_openapi_call_log
 */
@TableName(value = "system_openapi_call_log")
@Data
public class SystemOpenapiCallLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String requestId;

    private String credentialId;

    private String appId;

    private String accessKey;

    private String endpoint;

    private String httpMethod;

    /** data:list / data:read / data:add / data:edit / data:remove / flow:start / meta:list */
    private String action;

    private String tableCode;

    private String bizId;

    /** SUCCESS / DENIED / FAILED / RATE_LIMITED */
    private String result;

    private Integer statusCode;

    private String errorCode;

    private String errorMsg;

    private Long costMs;

    private String clientIp;

    private String nonce;

    /**
     * 流水表的归属列在 DDL 里可空：机器调用与异步线程没有 Web 上下文时
     * EntityHandler 会整体跳过自动填充（EntityHandler.java:47），所以写入方必须显式赋值
     */
    private String departmentId;

    private String organizationId;
}
