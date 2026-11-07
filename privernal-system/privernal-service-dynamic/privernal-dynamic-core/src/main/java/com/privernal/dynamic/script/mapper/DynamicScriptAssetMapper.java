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

package com.privernal.dynamic.script.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.dynamic.script.domain.DynamicScriptAsset;

/**
 * 脚本资产 Mapper（master 元数据库）。script_code 组织内唯一由服务层校验 + 库表 uk 兜底。
 */
@PrivernalDataScope(permissionMain = "dynamic:script")
public interface DynamicScriptAssetMapper extends PrivernalMapper<DynamicScriptAsset> {
}
