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

package com.privernal.dynamic.dbs.annotation.processor;

import com.privernal.dynamic.dbs.annotation.LoadEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-21 14:12
 * @Description LoadEntityBeanPostProcessor
 */

@Component
public class LoadEntityBeanPostProcessor implements BeanPostProcessor {

    private static Logger logger = LoggerFactory.getLogger(LoadEntityBeanPostProcessor.class);

    private static int AUTOWIRE_MODE = AutowireCapableBeanFactory.AUTOWIRE_BY_NAME;

    private static String ERROR_ENTITY_ANNOTATION_NOT_PRESENT = "@LoadEntity(value) is not annotated with @Entity";
    private static String ERROR_CREATE_INSTANCE = "Cannot create instance of type '{}' or instance creation is failed because: {}";

    @Autowired
    private ConfigurableListableBeanFactory configurableBeanFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
//        ReflectionUtils.FieldCallback fieldCallback = new LoadEntityFieldCallback(configurableBeanFactory, entityManager, bean);
//        ReflectionUtils.doWithFields(bean.getClass(), fieldCallback);

        Class<?> fieldType = bean.getClass();
        if (fieldType.getAnnotation(LoadEntity.class) != null) {
            long idValue = fieldType.getDeclaredAnnotation(LoadEntity.class).value();

            String beanName2 = String.format("@%s[%s=%d]", LoadEntity.class.getSimpleName(), fieldType.getSimpleName(), idValue);
            Object beanInstance = registerBeanInstance(beanName2, fieldType, idValue);

        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    public Object registerBeanInstance(String beanName, Class<?> fieldType, Long idValue) {
        Object beanInstance;
        if (!configurableBeanFactory.containsBean(beanName)) {
            logger.info("Loading fixture to new bean named '{}'.", beanName);

            Object entityValue;
            try {
                entityValue = entityManager.find(fieldType, idValue);
            } catch (Exception e) {
                logger.error(ERROR_CREATE_INSTANCE, fieldType.getTypeName(), e);
                throw new RuntimeException(e);
            }

            beanInstance = configurableBeanFactory.initializeBean(entityValue, beanName);
            configurableBeanFactory.autowireBeanProperties(beanInstance, AUTOWIRE_MODE, true);
            configurableBeanFactory.registerSingleton(beanName, beanInstance);
            logger.info("Bean named '{}' created successfully.", beanName);
        } else {
            beanInstance = configurableBeanFactory.getBean(beanName);
            logger.info("Bean named '{}' already exists used as current bean reference.", beanName);
        }
        return beanInstance;
    }
}
