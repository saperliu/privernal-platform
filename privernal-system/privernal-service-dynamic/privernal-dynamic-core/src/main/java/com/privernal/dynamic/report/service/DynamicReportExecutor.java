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

package com.privernal.dynamic.report.service;

import com.privernal.dynamic.report.mapper.DynamicReportQueryMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 报表聚合的数据源路由层。
 * <p>
 * 为什么单独一个类：{@code @DS("#database")} 要按参数名取库名，而
 * {@link DynamicReportQueryMapper} 的首参必须是物理表名——数据权限切面用
 * {@code args[0]} 拼 {@code dynamic:{table}:list}（PrivernalDataScopeAspect:206-211），
 * 把 database 插到第一位会让行级范围静默失效。所以在这里换一层：
 * 本类带 {@code @DS} 负责选库，mapper 保持首参为表名负责权限片段。
 * <p>
 * 少了这一层，聚合 SQL 会落到默认数据源（master）：业务库分离的组织里动态表根本不在 master，
 * 表现为「表不存在」或空结果——单库环境（组织业务库即 master）测试不出来，所以必须在分库组织上验证。
 */
@Component
public class DynamicReportExecutor {

    @Autowired
    private DynamicReportQueryMapper reportQueryMapper;

    /**
     * 聚合查询
     *
     * @param database   组织业务库名（调用方从登录上下文取，空则由 dynamic-datasource 回落 primary）
     * @param tableName  底层动态表物理表名（mapper 首参，供切面组装权限码）
     * @param querySql   服务层构建的聚合 SQL（值占位 {@code #{paramMap.pN}}）
     * @param paramMap   值绑定参数
     */
    @DS("#database")
    public List<Map<String, Object>> aggregate(String database, String tableName, String querySql,
                                               Map<String, Object> paramMap) {
        return reportQueryMapper.aggregate(tableName, querySql, paramMap);
    }

    /**
     * lookup 维度的批量显示值回查（一次 IN 查询，替代逐条 findById 的 N+1）
     */
    @DS("#database")
    public List<Map<String, Object>> lookupDisplay(String database, String tableName, String querySql,
                                                   Map<String, Object> paramMap) {
        return reportQueryMapper.lookupDisplay(tableName, querySql, paramMap);
    }
}
