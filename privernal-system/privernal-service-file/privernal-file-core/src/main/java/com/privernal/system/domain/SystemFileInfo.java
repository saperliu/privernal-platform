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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件信息对象 system_file_info
 *
 * @author saperliu
 * @date 2023-10-16
 * @Description 文件信息的实体
 */
@Data
public class SystemFileInfo extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 部门ID
     */
    private String departmentId;
    /**
     * 所属组织
     */
    private String organizationId;
    /**
     * 模块
     */
    private String module;
    /**
     * 来源ID
     */
    private String sourceId;
    /**
     * 来源内不同阶段的图片，如申请、审批、验收
     */
    private String sourceStage;
    /**
     * 类型: IMAGE,FILE
     */
    private String type;
    /**
     * 图片存储地址
     */
    private String imagePath;
    /**
     * 图片访问地址
     */
    private String imageUrl;
    /**
     * 原名称
     */
    private String originalName;
    /**
     * 新名称
     */
    private String newName;
    /**
     * 文件大小(字节数)
     */
    private Long fileSize;
    /**
     * 文件存储类型: OSS,MINIO,LOCAL
     */
    private String fileStoreType;


}
