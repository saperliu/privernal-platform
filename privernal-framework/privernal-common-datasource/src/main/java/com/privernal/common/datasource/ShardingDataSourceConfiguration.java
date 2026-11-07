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

package com.privernal.common.datasource;


//import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
//import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
//import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
//import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;
import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.infra.yaml.config.swapper.mode.YamlModeConfigurationSwapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;

@Configuration
//@ComponentScan("org.apache.shardingsphere.spring.boot.converter")
//@EnableConfigurationProperties(SpringBootPropertiesConfiguration.class)
//@AutoConfigureBefore({DynamicDataSourceAutoConfiguration.class, SpringBootConfiguration.class})
@RequiredArgsConstructor
@Slf4j
public class ShardingDataSourceConfiguration implements EnvironmentAware, ApplicationContextAware {

    /**
     * 分表数据源名称
     */
    public static final String SHARDING_DATA_SOURCE_NAME = "sharding";

    private ApplicationContext applicationContext;

//    private final SpringBootPropertiesConfiguration props;

    /**
     * ShardingDataSource 的所有数据源
     */
    private final Map<String, DataSource> dataSourceMap = new LinkedHashMap<>();

    private String databaseName;
    /**
     * 动态数据源配置项
     */
//    @Resource
//    private DynamicDataSourceProperties dynamicDataSourceProperties;

    @Lazy
    @Resource
    DataSource shardingDataSource;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

//    @Bean
//    public ModeConfiguration modeConfiguration() {
//        return null == this.props.getMode() ? null : (new YamlModeConfigurationSwapper()).swapToObject(this.props.getMode());
//    }
//
//    @Bean
//    @Conditional(LocalRulesCondition.class)
//    @Autowired(required = false)
//    public ShardingSphereDataSource localRulesCondition(final ObjectProvider<List<RuleConfiguration>> rules, final ObjectProvider<ModeConfiguration> modeConfig) throws SQLException {
//        Collection<RuleConfiguration> ruleConfigs = Optional.ofNullable(rules.getIfAvailable()).orElseGet(Collections::emptyList);
//        DataSource dataSource = ShardingSphereDataSourceFactory.createDataSource(databaseName, modeConfig.getIfAvailable(), dataSourceMap, ruleConfigs, props.getProps());
////        DynamicRoutingDataSource dynamicRoutingDataSource = applicationContext.getBean(DynamicRoutingDataSource.class);
////        if (ObjectUtil.isNotNull(dynamicRoutingDataSource)) {
////            dynamicRoutingDataSource.addDataSource(SHARDING_DATA_SOURCE_NAME, dataSource);
////        }
//        log.info(" local rules condition datasource");
//        dataSourceMap.put(SHARDING_DATA_SOURCE_NAME, dataSource);
//        return (ShardingSphereDataSource) dataSource;
//    }


//    @Bean
//    @ConditionalOnClass(ModeConfiguration.class)
//    public ShardingSphereDataSource modeConfig(final ModeConfiguration modeConfig) throws SQLException {
//        DataSource dataSource = !dataSourceMap.isEmpty() ? ShardingSphereDataSourceFactory.createDataSource(databaseName, modeConfig, dataSourceMap, Collections.emptyList(), props.getProps()) : ShardingSphereDataSourceFactory.createDataSource(databaseName, modeConfig);
////        DynamicRoutingDataSource dynamicRoutingDataSource = applicationContext.getBean(DynamicRoutingDataSource.class);
////        if (ObjectUtil.isNotNull(dynamicRoutingDataSource)) {
////            dynamicRoutingDataSource.addDataSource(SHARDING_DATA_SOURCE_NAME, dataSource);
////        }
//        dataSourceMap.put(SHARDING_DATA_SOURCE_NAME,dataSource);
//        return (ShardingSphereDataSource) dataSource;
//    }


    /**
     * 将shardingDataSource放到了多数据源（dataSourceMap）中
     */
//    @Bean
//    public DynamicDataSourceProvider dynamicDataSourceProvider() {
////        DefaultDataSourceCreator defaultDataSourceCreator = new DefaultDataSourceCreator();
////        Map<String, DataSourceProperty> sourceMap = dynamicDataSourceProperties.getDatasource();
//        dataSourceMap.put(SHARDING_DATA_SOURCE_NAME, shardingDataSource);
//        return new DynamicDataSourceProvider() {
//            @Override
//            public Map<String, DataSource> loadDataSources() {
////                Map<String, DataSource> dataSourceDynamicMap = createDataSourceMap(sourceMap);
//                // 将 shardingJdbc 所有数据源配置到 dynamic-datasource
////                dataSourceDynamicMap.putAll(dataSourceMap);
//                return dataSourceMap;
//            }
//        };
//    }

    /**
     * 将动态数据源设置为首选的
     * 当spring存在多个数据源时, 自动注入的是首选的对象
     * 设置为主要的数据源之后，就可以支持shardingJdbc原生的配置方式了
     *
     * @return DataSource
     */
//    @Primary
//    @Bean
//    public DataSource dataSource() {
//        DynamicRoutingDataSource dataSource = new DynamicRoutingDataSource();
//        dataSource.setPrimary(SHARDING_DATA_SOURCE_NAME);
//        dataSource.setStrict(dynamicDataSourceProperties.getStrict());
//        dataSource.setStrategy(dynamicDataSourceProperties.getStrategy());
//        dataSource.setP6spy(dynamicDataSourceProperties.getP6spy());
//        dataSource.setSeata(dynamicDataSourceProperties.getSeata());
//        return dataSource;
//    }
    @Override
    public final void setEnvironment(final Environment environment) {
//        dataSourceMap.putAll(DataSourceMapSetter.getDataSourceMap(environment));
//        databaseName = DatabaseNameSetter.getDatabaseName(environment);
    }
}
