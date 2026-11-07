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

package com.privernal.dynamic.datav.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.dynamic.datav.domain.DynamicDatavAsset;

/**
 * 大屏素材 mapper
 * <p>
 * 类注解必须有：数据权限切面对无注解 mapper 的兜底分支直接判 1=2 拒绝。
 * 素材跟随大屏的权限面（能编辑大屏的人才能管理素材），不另开授权面。
 *
 * @author saperliu
 */
@PrivernalDataScope(permissionMain = "datav:screen")
public interface DynamicDatavAssetMapper extends PrivernalMapper<DynamicDatavAsset> {
}
