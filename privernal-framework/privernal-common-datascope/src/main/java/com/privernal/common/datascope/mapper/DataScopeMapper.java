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

package com.privernal.common.datascope.mapper;

/**
 * 数据权限切面的标记接口。
 *
 * <p>{@code PrivernalDataScopeAspect} 的切点原本只覆盖 {@code PrivernalMapper} 体系，
 * 手写 SQL、无实体泛型的 mapper（如动态表数据 mapper）落入
 * {@code DataPermissionHandler} 兜底分支后拿不到表名，按表配置的角色数据范围因此整段失效。
 * 继承本接口即可并入同一套权限处理，且不必承担 {@code BaseMapper<T>} 的实体约束。
 */
public interface DataScopeMapper {
}
