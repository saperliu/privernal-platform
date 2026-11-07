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

package com.privernal.dynamic.advanced.mapper;

import com.privernal.dynamic.advanced.domain.DynamicFormShare;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.common.datascope.annotation.PrivernalDataScope;
import org.apache.ibatis.annotations.Param;

/**
 * 表单外链配置 Mapper（master 元数据库，免登入口在 master 上按 token 摘要定位组织）。
 * <p>
 * SQL 在 XML（resources/mapper/dynamic/DynamicFormShareMapper.xml）：时间/配额表达式
 * 含 MySQL/SQLite 方言差异，走 privernal.dialect 片段的 databaseId 变体；
 * 注解 SQL 无法按 databaseId 分支，故统一迁 XML。
 * </p>
 */
@PrivernalDataScope(permissionMain = "dynamic:form")
public interface DynamicFormShareMapper extends PrivernalMapper<DynamicFormShare> {

    /**
     * 原子消费一次提交名额：仅当启用、未过期、未达上限时 submit_count+1。
     * 返回 0 行 = 名额不可用（停用/过期/超限），调用方对外一律 404，不区分原因（§5.3-4）。
     */
    int consumeSubmitQuota(@Param("shareId") String shareId);

    /**
     * 提交成功后回退名额（后续落库/回执生成失败时补偿，避免幽灵占额）
     */
    int releaseSubmitQuota(@Param("shareId") String shareId);
}
