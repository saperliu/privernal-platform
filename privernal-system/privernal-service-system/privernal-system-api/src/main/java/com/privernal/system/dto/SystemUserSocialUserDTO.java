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

import com.privernal.common.web.domain.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * 社交帐号关联系统账号对象 system_user_social_user
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 社交帐号关联系统账号对象 system_user_social_user
 */
@Tag(name = "SystemUserSocialUserDTO", description = "社交帐号关联系统账号对应的DTO")
@Data
public class SystemUserSocialUserDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     */
    @Schema(description = "")

    private String id;

    /**
     * 系统用户ID
     */
    @Schema(description = "系统用户ID")

    private String userId;

    /**
     * 社交用户ID
     */
    @Schema(description = "社交用户ID")

    private String socialUserId;

}
