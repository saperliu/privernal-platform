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

package com.privernal.dynamic.dto;

import com.privernal.common.web.domain.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * 动态数据库对象 dynamic_database
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description 动态数据库对象 dynamic_database
 */
@Tag(name = "DynamicDatabaseDTO", description = "动态数据库对应的DTO")
@Data
public class DynamicDatabaseDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 所属部门
     */
    @Schema(description = "所属部门")

    private String departmentId;

    /**
     * 所属组织
     */
    @Schema(description = "所属组织")

    private String organizationId;

    /**
     * 数据标题
     */
    @Schema(description = "数据标题")

    private String title;

    /**
     * 状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消
     */
    @Schema(description = "状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消")

    private Long status;

    /**
     * 数据库名称
     */
    @Schema(description = "数据库名称")

    private String databaseName;

    /**
     * 数据库的类型
     */
    @Schema(description = "数据库的类型")

    private String databaseType;

    /**
     * 数据库使用的驱动
     */
    @Schema(description = "数据库使用的驱动")

    private String driver;

    /**
     * 数据库的密码
     */
    @Schema(description = "数据库的密码")

    private String password;

    /**
     * 使用的URL连接
     */
    @Schema(description = "使用的URL连接")

    private String url;

    /**
     * 数据库的用户名
     */
    @Schema(description = "数据库的用户名")

    private String userName;

}
