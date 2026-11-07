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

package com.privernal.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * OpenAPI 凭证的按表授权项，对应 system_openapi_credential.table_grant 里的一个元素
 */
@Data
@Schema(description = "OpenAPI按表授权项")
public class OpenApiTableGrantDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "动态表编码（含组织前缀的物理编码）")
    private String tableCode;

    @Schema(description = "允许的动作;list/read/add/edit/remove")
    private List<String> actions;
}
