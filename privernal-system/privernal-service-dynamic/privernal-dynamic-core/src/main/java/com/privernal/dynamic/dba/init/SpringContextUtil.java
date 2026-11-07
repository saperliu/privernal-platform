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

package com.privernal.dynamic.dba.init;

import com.privernal.dynamic.dba.constants.Constants;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.PropertyResourceConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.support.PropertiesLoaderSupport;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Properties;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-21 14:12
 * @Description bean的操作
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    private static Properties properties = null;

    @Value(Constants.TABLE_AUTO_KEY_VALUE)
    private String tableAuto;

    @Value(Constants.MODEL_PACK_KEY_VALUE)
    private String modelPack;

    @Value(Constants.DATABASE_TYPE_KEY_VALUE)
    private String databaseType;

    /**
     * 实现ApplicationContextAware接口的回调方法，设置上下文环境
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringContextUtil.applicationContext = applicationContext;
    }

    /**
     * 获得spring上下文
     *
     * @return ApplicationContext spring上下文
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 获取bean
     *
     * @param name service注解方式name为小驼峰格式
     * @return Object bean的实例对象
     */
    public Object getBean(String name) throws BeansException {
        return applicationContext.getBean(name);
    }

    /**
     * 获取配置文件的值，兼容传统spring项目和springboot项目
     *
     * @param key properties
     * @return
     */
    public String getConfig(String key) {
        if (Constants.DATABASE_TYPE_KEY.equals(key) && !Constants.NULL.equals(databaseType)) {
            return databaseType;
        }
        if (Constants.MODEL_PACK_KEY.equals(key) && !Constants.NULL.equals(modelPack)) {
            return modelPack;
        }
        if (Constants.TABLE_AUTO_KEY.equals(key) && !Constants.NULL.equals(tableAuto)) {
            return tableAuto;
        }
        if (properties == null) {
            initProperties();
        }
        Object object = properties.get(key);
        return object == null ? null : (String) object;
    }

    private void initProperties() {

        properties = new Properties();
        try {
            String[] postProcessorNames = applicationContext.getBeanNamesForType(BeanFactoryPostProcessor.class, true,
                    true);
            for (String ppName : postProcessorNames) {
                BeanFactoryPostProcessor beanProcessor = applicationContext.getBean(ppName,
                        BeanFactoryPostProcessor.class);
                if (beanProcessor instanceof PropertyResourceConfigurer) {
                    PropertyResourceConfigurer propertyResourceConfigurer = (PropertyResourceConfigurer) beanProcessor;
                    Method mergeProperties = PropertiesLoaderSupport.class.getDeclaredMethod("mergeProperties");
                    mergeProperties.setAccessible(true);
                    Properties props = (Properties) mergeProperties.invoke(propertyResourceConfigurer);

                    // get the method convertProperties
                    // in class PropertyResourceConfigurer
                    Method convertProperties = PropertyResourceConfigurer.class.getDeclaredMethod("convertProperties",
                            Properties.class);
                    // convert properties
                    convertProperties.setAccessible(true);
                    convertProperties.invoke(propertyResourceConfigurer, props);

                    properties.putAll(props);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
