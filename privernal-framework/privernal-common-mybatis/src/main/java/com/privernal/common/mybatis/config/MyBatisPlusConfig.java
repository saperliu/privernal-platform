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

package com.privernal.common.mybatis.config;

import com.privernal.common.datascope.aspect.PrivernalDataScopeAspect;
import com.privernal.common.datascope.aspect.OrganizationHandlerImpl;
import com.privernal.common.datascope.aspect.OrganizationLineInnerInterceptor;
import com.privernal.common.mybatis.mapper.DeleteFieldFillInterceptor;
import com.privernal.common.mybatis.mapper.PrivernalSqlInjector;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2021-06-11 17:07
 * @Description 处理自动插入和更新的字段
 */
@Configuration
public class MyBatisPlusConfig {

    @Autowired
    private PrivernalDataScopeAspect privernalDataScopeAspect;

    /**
     * 自动填充功能
     * @return GlobalConfig
     */
    @Bean
    public GlobalConfig globalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(new EntityHandler());
        return globalConfig;
    }
    /**
     * 新版
     * 新多租户插件配置,一缓和二缓遵循mybatis的规则,需要设置 MybatisConfiguration#useDeprecatedExecutor = false 避免缓存万一出现问题
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 如果用了分页插件注意先 add TenantLineInnerInterceptor 再 add PaginationInnerInterceptor
        // 用了分页插件必须设置 MybatisConfiguration#useDeprecatedExecutor = false
        // interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(new OrganizationLineInnerInterceptor(new OrganizationHandlerImpl()));
        mybatisPlusInterceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // todo 目前使用了2种分页插件
        mybatisPlusInterceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        // 添加自定义的数据权限处理器
        DataPermissionInterceptor dataPermissionInterceptor = new DataPermissionInterceptor();
        dataPermissionInterceptor.setDataPermissionHandler(privernalDataScopeAspect);
        mybatisPlusInterceptor.addInnerInterceptor(dataPermissionInterceptor);

        return mybatisPlusInterceptor;
    }

    /**
     * 注入自定义的SQL注入器
     * @return
     */
    @Bean
    public PrivernalSqlInjector perfectSqlInjector() {
        return new PrivernalSqlInjector();
    }

    /**
     * 注入自定义的拦截器，处理删除时的自动填充
     * @return
     */
    @Bean
    public DeleteFieldFillInterceptor deleteFieldFillInterceptor() {
        return new DeleteFieldFillInterceptor();
    }


    /**
     为了配置管理我把这段代码加入到了 MybatisPlusConfig 中
     */
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.put("Oracle","oracle");
        properties.put("MySQL","mysql");
        properties.setProperty("SQL Server", "sqlserver");
        properties.put("PostgreSQL", "postgresql");
        properties.put("SQLite", "sqlite");
        databaseIdProvider.setProperties(properties);
        return databaseIdProvider;
    }

}
