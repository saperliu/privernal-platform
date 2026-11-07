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

/**
 * 文件信息
 *
 * @author saperliu
 */
@Data
public class SystemFileDTO implements Serializable {

    /**
     * 主键
     */
    @Schema(description = "主键")
    private String id;

    /**
     * 文件名称
     */
    private String name;

    /**
     * 文件原名
     */
    private String originalName;

    /**
     * 文件地址
     */
    private String url;

    /**
     * 文件路径
     */
    private String path;

    /**
     * 如果是识别二维码，则是二维码的内容
     */
    private String qrCode;

}
