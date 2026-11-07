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

package com.privernal.system.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemAiChatMessage;

/**
 * AI 问数对话消息 Mapper。类注解必须有：数据范围切面对无注解的 com.privernal mapper 会把 WHERE 置 1=2（实测踩坑）。
 * ignoreDepartment + permissionMain：消息只按组织+用户隔离（见 service），不走部门数据范围。
 */
@PrivernalDataScope(permissionMain = "system:aiChatMessage", ignoreDepartment = true)
public interface SystemAiChatMessageMapper extends PrivernalMapper<SystemAiChatMessage> {
}
