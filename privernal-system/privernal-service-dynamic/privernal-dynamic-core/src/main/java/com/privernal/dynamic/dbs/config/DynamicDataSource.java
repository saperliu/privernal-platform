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

package com.privernal.dynamic.dbs.config;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.provider.AbstractJdbcDataSourceProvider;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-27 16:43
 * @Description 项目启动读取配置的数据库配置信息
 */
@Configuration
@Slf4j
public class DynamicDataSource {


    @Value("${spring.datasource.dynamic.datasource.master.driver-class-name}")
    String driverName;

    @Value("${spring.datasource.dynamic.datasource.master.url}")
    String url;

    @Value("${spring.datasource.dynamic.datasource.master.username}")
    String userName;

    @Value("${spring.datasource.dynamic.datasource.master.password}")
    String password;

    @Bean
    public DynamicDataSourceProvider dynamicDataSourceProvider(DefaultDataSourceCreator springManagedCreator) {

        // 必须注入 Spring 管理的 DefaultDataSourceCreator：starter 自动配置会装配全部
        // DataSourceCreator 实现（druid/hikari 等）。此前 new DefaultDataSourceCreator()
        // 的 creators 列表为空——executeStmt 日志照常打印库名，但建池全部静默失败，
        // 业务库从未真正注册为数据源，@DS("#database") 一律回落/报错（2026-10-04
 // 用例 8b 实测发现：CannotFindDataSourceException dynamic_probe_b，
        // 启动日志仅有 master 一个 Druid 池）。
        return new AbstractJdbcDataSourceProvider(springManagedCreator, url, userName, password) {
            @Override
            protected Map<String, DataSourceProperty> executeStmt(Statement statement)
                    throws SQLException {
                Map<String, DataSourceProperty> map = new HashMap<>();

                // 表名为 dynamic_database（实体 DynamicDatabase 的 @TableName 与此一致）。
                // 此处历史上写的是 system_database，而任何脚本都从未创建过它，
                // 导致启动加载业务库静默失败、@DS("#database") 全部回落 master。
                // deleted_flag 可能为 NULL，所以只排除显式 'Y'，避免把在用的库过滤掉。
                ResultSet rs = statement.executeQuery(
                        "select database_name, user_name, password, url, driver from dynamic_database "
                                + "where ifnull(deleted_flag, 'N') <> 'Y'");
                while (rs.next()) {
                    String name = rs.getString("database_name");
                    String username = rs.getString("user_name");
                    String password = rs.getString("password");
                    String url = rs.getString("url");
                    String driver = rs.getString("driver");
                    log.info(" name   {}   ", name);
                    DataSourceProperty property = new DataSourceProperty();
                    property.setUsername(username);
                    property.setPassword(password);
                    property.setUrl(url);
                    property.setDriverClassName(driver);
                    map.put(name, property);
                }
                return map;
            }
        };
    }
}
