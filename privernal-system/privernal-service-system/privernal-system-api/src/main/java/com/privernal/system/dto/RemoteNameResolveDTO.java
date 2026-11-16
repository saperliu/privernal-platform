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
 * 名称批量解析请求（R1-8 权限审计报表用）
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：名称批量解析请求
 */
@Schema(name = "RemoteNameResolveDTO", description = "名称批量解析请求")
@Data
public class RemoteNameResolveDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID集合")
    private List<String> userIds;

    @Schema(description = "角色ID集合")
    private List<String> roleIds;
}
