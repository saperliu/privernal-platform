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
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serializable;

/**
 * 系统配置对象 system_default_config
 *
 * @author saperliu
 * @date 2023-06-04
 * @Description 系统配置对象 system_default_config 的value的json值
 */
@Tag(name = "AppSystemConfigDTO", description = "系统配置对应的DTO")
@Data
public class AppSystemConfigDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * app请求的网址
     */
    @Schema(description = "app请求的网址")
    private String url;

    /**
     * 钉钉授权登录应用的KEY
     */
    @Schema(description = "钉钉授权登录应用的KEY")
    private String dingAppKey;
    /**
     * 创建应用的agentId
     */
    @Schema(description = "创建应用的agentId")
    private String agentId;
    /**
     * 创建应用的企业ID
     */
    @Schema(description = "创建应用的企业ID")
    private String corpId;
    /**
     * 创建应用的scheme
     */
    @Schema(description = "创建应用的scheme")
    private String scheme;

}
