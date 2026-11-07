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

package com.privernal.common.mybatis.mapper;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.privernal.common.security.service.SecurityUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Properties;


@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
public class DeleteFieldFillInterceptor implements Interceptor {


    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        // 获取 SQL 命令
        SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
        //只判断修改，逻辑删除时是更新
        if (SqlCommandType.UPDATE.equals(sqlCommandType)) {
            // 只处理 DeleteFillById 方法，填充删除日期和删除人。因为只有逻辑删除时用到，所以逻辑删除是更新。
            if (mappedStatement.getId().contains(DeleteFillById.MAPPER_METHOD)) {
                // 获取参数
                Object parameter = invocation.getArgs()[1];
                //批量操作时
                boolean flag = false;
                if (parameter instanceof MapperMethod.ParamMap) {
                    MapperMethod.ParamMap map = (MapperMethod.ParamMap) parameter;
                    Object obj = map.get("list");
                    List<?> list = (List<?>) obj;
                    if (list != null) {
                        for (Object o : list) {
                            setParameter(o, parameter, flag);
                        }
                    }
                } else if (parameter instanceof String) {
                    // 在删除方法里只传了Id，不会是对象，需要手动实例对象
                    Class<?> clazzReal = mappedStatement.getParameterMap().getType();
                    Object instanceObj = Class.forName(clazzReal.getName()).newInstance();
                    invocation.getArgs()[1] = instanceObj;
                    flag = true;
                    setParameter(instanceObj, parameter, flag);
                } else {
                    setParameter(parameter, parameter, flag);
                }
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        Interceptor.super.setProperties(properties);
    }

    public void setParameter(Object parameter, Object argsId, boolean flag) throws Throwable {
        Class<?> aClass = parameter.getClass();
        Field[] declaredFields;
        //判断BaseEntity是否是父类
        if (BaseEntity.class.isAssignableFrom(aClass)) {
            // 获取父类私有成员变量
            declaredFields = aClass.getSuperclass().getDeclaredFields();
            Field[] fields = aClass.getDeclaredFields();
            declaredFields = ArrayUtils.addAll(declaredFields, fields);
        } else {
            // 获取私有成员变量
            declaredFields = aClass.getDeclaredFields();
        }
        for (Field field : declaredFields) {

            if (field.getAnnotation(DeleteDate.class) != null) {
                field.setAccessible(true);
                field.set(parameter, new Date());
            }
            if (field.getAnnotation(DeleteBy.class) != null) {
                field.setAccessible(true);
                // 当前用户名
                field.set(parameter, SecurityUtils.getUserId());
            }
            if (flag) {
                if (field.getName().equals("id")) {
                    field.setAccessible(true);
                    // 当只是ID参数时，需要这里重置参数
                    field.set(parameter, argsId);
                }
            }
        }
    }
}
