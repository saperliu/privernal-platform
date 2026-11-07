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
 * 用户服务组织对象 system_user_service_org
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户服务组织的过滤器
 */
@Tag(name = "SystemUserServiceOrgFilterDTO", description = "用户服务组织对应的查询过滤器")
@Data
public class SystemUserServiceOrgFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */
    @Schema(description = "主键")
    private String id;


    /**
     * 用户ID;用户ID
     */
    @Schema(description = "用户ID;用户ID")
    private String userId;


    /**
     * 组织ID;服务的组织ID
     */
    @Schema(description = "组织ID;服务的组织ID")
    private String organizationId;


    /**
     * 数据权限（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）
     */
    @Schema(description = "数据权限（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）")
    private Long dataScope;


    /**
     * 部门ID;兼职部门ID
     */
    @Schema(description = "部门ID;兼职部门ID")
    private String departmentId;


}
