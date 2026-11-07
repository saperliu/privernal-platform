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

package com.privernal.system.filter;

import com.privernal.common.web.domain.FilterDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * 文件配置对象 system_file_config
 *
 * @author saperliu
 * @date 2023-10-16
 * @Description 文件配置的过滤器
 */
@Tag(name = "SystemFileConfigFilterDTO", description = "文件配置对应的查询过滤器")
@Data
public class SystemFileConfigFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 部门ID
     */
    @Schema(description = "部门ID")
    private String departmentId;


    /**
     * 所属组织
     */
    @Schema(description = "所属组织")
    private String organizationId;


    /**
     * 标题
     */
    @Schema(description = "标题")
    private String title;


    /**
     * 名称
     */
    @Schema(description = "名称")
    private String name;


    /**
     * 类型: OSS，LOCAL,MINIO
     */
    @Schema(description = "类型: OSS，LOCAL,MINIO")
    private String type;


    /**
     * 存储地址
     */
    @Schema(description = "存储地址")
    private String endpoint;


    /**
     * 存储的ID
     */
    @Schema(description = "存储的ID")
    private String accessId;


    /**
     * 存储的KEY
     */
    @Schema(description = "存储的KEY")
    private String accessKey;


    /**
     * 桶
     */
    @Schema(description = "桶")
    private String bucket;


    /**
     * 存储的目录
     */
    @Schema(description = "存储的目录")
    private String storePath;


    /**
     * 网络访问的地址
     */
    @Schema(description = "网络访问的地址")
    private String netPath;


}
