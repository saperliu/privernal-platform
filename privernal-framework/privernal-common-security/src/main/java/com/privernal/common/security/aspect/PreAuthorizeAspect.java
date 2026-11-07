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

package com.privernal.common.security.aspect;

import com.privernal.common.core.exception.PreAuthorizeException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.TokenService;
import com.privernal.system.dto.UserInfo;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.PatternMatchUtils;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * 自定义权限实现
 *
 * @author saperliu
 */
@Slf4j
@Aspect
@Component
public class PreAuthorizeAspect
{
    @Autowired
    private TokenService tokenService;

    /** 所有权限标识 */
    private static final String ALL_PERMISSION = "*:*:*";

    /** 数组为0时 */
    private static final Integer ARRAY_EMPTY = 0;

    @Around("@annotation(com.privernal.common.security.annotation.PreAuthorize)")
    public Object around(ProceedingJoinPoint point) throws Throwable
    {
        Signature signature = point.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        if (annotation == null)
        {
            return point.proceed();
        }

        log.info("PreAuthorize hasPermi：{}", annotation.hasPermi());
        log.info("PreAuthorize lacksPermi：{}", annotation.lacksPermi());
        log.info("PreAuthorize hasAnyPermi：{}", JSON.toJSONString(annotation.hasAnyPermi()));

        if (StringUtils.isNotEmpty(annotation.hasPermi()))
        {
            if (hasPermi(annotation.hasPermi()))
            {
                return point.proceed();
            }
            throw new PreAuthorizeException();
        }
        else if (StringUtils.isNotEmpty(annotation.lacksPermi()))
        {
            if (lacksPermi(annotation.lacksPermi()))
            {
                return point.proceed();
            }
            throw new PreAuthorizeException();
        }
        else if (ARRAY_EMPTY < annotation.hasAnyPermi().length)
        {
            if (hasAnyPermi(annotation.hasAnyPermi()))
            {
                return point.proceed();
            }
            throw new PreAuthorizeException();
        }

        return point.proceed();
    }

    /**
     * 验证用户是否具备某权限
     *
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    public boolean hasPermi(String permission)
    {
        UserInfo userInfo = tokenService.getLoginUser();
        if (StringUtils.isNull(userInfo) || CollectionUtils.isEmpty(userInfo.getPermissions()))
        {
            log.info("PreAuthorize userInfo：{}", JSON.toJSONString(userInfo));
            return false;
        }
        return hasPermissions(userInfo.getPermissions(), permission);
    }

    /**
     * 验证用户是否不具备某权限，与 hasPermi逻辑相反
     *
     * @param permission 权限字符串
     * @return 用户是否不具备某权限
     */
    public boolean lacksPermi(String permission)
    {
        return hasPermi(permission) != true;
    }

    /**
     * 验证用户是否具有以下任意一个权限
     *
     * @param permissions 权限列表
     * @return 用户是否具有以下任意一个权限
     */
    public boolean hasAnyPermi(String[] permissions)
    {
        UserInfo userInfo = tokenService.getLoginUser();
        if (StringUtils.isNull(userInfo) || CollectionUtils.isEmpty(userInfo.getPermissions()))
        {
            return false;
        }
        Collection<String> authorities = userInfo.getPermissions();
        for (String permission : permissions)
        {
            if (permission != null && hasPermissions(authorities, permission))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否包含权限
     *
     * @param authorities 权限列表
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    private boolean hasPermissions(Collection<String> authorities, String permission)
    {
        return authorities.stream().filter(StringUtils::hasText)
                .anyMatch(x -> ALL_PERMISSION.contains(x) || PatternMatchUtils.simpleMatch(x, permission));
    }
}
