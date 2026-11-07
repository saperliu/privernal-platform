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


import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.constant.HttpStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.ServletUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.annotation.ServiceRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2021-10-01 21:48
 * @Description 只开放给内部服务之间访问，对外不开放
 */
@Aspect
@Component
public class ServiceRequestAspect implements Ordered
{
    @Around("@annotation(serviceRequest)")
    public Object innerAround(ProceedingJoinPoint point, ServiceRequest serviceRequest) throws Throwable
    {
        String request = ServletUtils.getRequest().getHeader(Constants.SERVICE_REQUEST);
        // 内部请求验证
        if (!StringUtils.equals(Constants.REQUEST_INNER, request))
        {
            throw new ServiceException("内部服务，不允许访问", HttpStatus.FORBIDDEN+"");
        }
        return point.proceed();
    }

    /**
     * 确保在权限认证aop执行前执行
     */
    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
