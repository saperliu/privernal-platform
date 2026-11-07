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
 * 用户组与用户对象 system_user_group_user
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户组与用户的过滤器
 */
@Tag(name = "SystemUserGroupUserFilterDTO", description = "用户组与用户对应的查询过滤器")
@Data
public class SystemUserGroupUserFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */
    @Schema(description = "主键")
    private String id;


    /**
     * 用户组ID
     */
    @Schema(description = "用户组ID")
    private String groupId;


    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    private String userId;

    /**
     * 用户昵称
     */
    @Schema(description = "用户昵称")
    private String nickName;
}
