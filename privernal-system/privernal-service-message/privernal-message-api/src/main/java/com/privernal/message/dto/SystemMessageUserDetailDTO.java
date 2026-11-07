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

package com.privernal.message.dto;

import com.privernal.common.web.domain.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * 系统通知人员对象 system_message_user
 *
 * @author saperliu
 * @date 2024-03-11
 * @Description 系统通知人员对象 system_message_user
 */
@Tag(name = "SystemMessageUserDetailDTO", description = "系统通知人员对应的详情DTO")
@Data
public class SystemMessageUserDetailDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 消息ID
     */
    @Schema(description = "消息ID")

    private String messageId;

    /**
     * 用户
     */
    @Schema(description = "用户")

    private String userId;

    /**
     * 状态;（0未读 1已读）
     */
    @Schema(description = "状态;（0未读 1已读）")

    private Long status;

    /**
     * 昵称
     */
    @Schema(description = "昵称")

    private String nickName;

}
