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
 * 角色用户与部门关系对象 system_role_user_dept
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色用户与部门关系的过滤器
 */
@Tag(name = "SystemRoleUserDeptFilterDTO", description = "角色用户与部门关系对应的查询过滤器")
@Data
public class SystemRoleUserDeptFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */
    @Schema(description = "主键")
    private String id;


    /**
     * 角色用户表的ID;角色用户表的ID
     */
    @Schema(description = "角色用户表的ID;角色用户表的ID")
    private String roleUserId;


    /**
     * 组织ID;所属组织
     */
    @Schema(description = "组织ID;所属组织")
    private String organizationId;


    /**
     * 部门ID;服务的部门
     */
    @Schema(description = "部门ID;服务的部门")
    private String departmentId;

    /**
     * 手动授权【1】/自动授权【2】
     *  默认手动授权
     */
    @Schema(description = "手动授权【1】/自动授权【2】")
    private int authWay;
}
