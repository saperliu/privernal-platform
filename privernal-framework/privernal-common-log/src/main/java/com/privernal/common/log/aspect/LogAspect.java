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

package com.privernal.common.log.aspect;


import com.privernal.common.core.utils.ServletUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.core.utils.ip.IpUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.BusinessStatus;
import com.privernal.common.log.service.AsyncLogService;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.message.dto.SystemLogOperationDTO;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 操作日志记录处理
 *
 * @author saperliu
 */
@Aspect
@Component
public class LogAspect
{
    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    @Autowired
    private AsyncLogService asyncLogService;

    // 配置织入点
    @Pointcut("@annotation(com.privernal.common.log.annotation.Log)")
    public void logPointCut()
    {
    }

    /**
     * 处理完请求后执行
     *
     * @param joinPoint 切点
     */
    @AfterReturning(pointcut = "logPointCut()", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, Object jsonResult)
    {
        handleLog(joinPoint, null, jsonResult);
    }

    /**
     * 拦截异常操作
     *
     * @param joinPoint 切点
     * @param e 异常
     */
    @AfterThrowing(value = "logPointCut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e)
    {
        handleLog(joinPoint, e, null);
    }

    protected void handleLog(final JoinPoint joinPoint, final Exception e, Object jsonResult)
    {
        try
        {
            // 获得注解
            Log controllerLog = getAnnotationLog(joinPoint);
            if (controllerLog == null)
            {
                return;
            }

            // *========数据库日志=========*//
            SystemLogOperationDTO operLog = new SystemLogOperationDTO();
            operLog.setStatus(Long.parseLong(BusinessStatus.SUCCESS.ordinal()+""));
            // 请求的地址
            String ip = IpUtils.getIpAddr(ServletUtils.getRequest());
            operLog.setOperationIp(ip);
            // 返回参数：与 operation_param 同口径截断。不截时大返回体（如按模板批量取数，
            // 单次可达上万行明文）会整份写进日志表——既是膨胀，也是一份敏感数据副本
            operLog.setOperationResult(StringUtils.substring(maskSensitive(JSON.toJSONString(jsonResult)), 0, 900));

            operLog.setOperationUrl(ServletUtils.getRequest().getRequestURI());
            String username = SecurityUtils.getLoginName();
            if (StringUtils.isNotBlank(username))
            {
                operLog.setOperationPerson(username);
            }
            String organizationId = SecurityUtils.getOrganizationId();

            if (StringUtils.isNotBlank(organizationId))
            {
                operLog.setOrganizationId(organizationId);
            }
            String departmentId = SecurityUtils.getDepartmentId();
            if (StringUtils.isNotBlank(departmentId))
            {
                operLog.setDepartmentId(departmentId);
            }

            if (e != null)
            {
                operLog.setStatus(Long.parseLong(BusinessStatus.FAIL.ordinal()+""));
                operLog.setMessage(StringUtils.substring(e.getMessage(), 0, 900));
            }
            // 设置方法名称
            String className = joinPoint.getTarget().getClass().getName();
            String methodName = joinPoint.getSignature().getName();
            operLog.setMethod(className + "." + methodName + "()");
            // 设置请求方式
            operLog.setRequestMethod(ServletUtils.getRequest().getMethod());
            // 处理设置注解上的参数
            getControllerMethodDescription(joinPoint, controllerLog, operLog);
            // 保存数据库
            asyncLogService.saveSysLog(operLog);
        }
        catch (Exception exp)
        {
            log.error("记录操作日志时发生异常", exp);
        }
    }

    /**
     * 获取注解中对方法的描述信息 用于Controller层注解
     *
     * @param log 日志
     * @param operLog 操作日志
     * @throws Exception
     */
    public void getControllerMethodDescription(JoinPoint joinPoint, Log log, SystemLogOperationDTO operLog) throws Exception
    {
        // 设置操作人类别
        operLog.setBusinessType(log.businessType().ordinal()+"");
        // 设置标题
        operLog.setName(log.title());
        // 设置action动作
        operLog.setOperationType(log.operationType().ordinal()+"");
        // 是否需要保存request，参数和值
        if (log.isSaveRequestData())
        {
            // 获取参数的信息，传入到数据库中。
            setRequestValue(joinPoint, operLog);
        }
    }

    /**
     * 获取请求的参数，放到log中
     *
     * @param operLog 操作日志
     * @throws Exception 异常
     */
    private void setRequestValue(JoinPoint joinPoint, SystemLogOperationDTO operLog) throws Exception
    {
        String requestMethod = operLog.getRequestMethod();
        if (HttpMethod.PUT.name().equals(requestMethod) || HttpMethod.POST.name().equals(requestMethod))
        {
            String params = argsArrayToString(joinPoint.getArgs());
            operLog.setOperationParam(StringUtils.substring(maskSensitive(params), 0, 900));
        }
    }

    /**
     * 是否存在注解，如果存在就获取
     */
    private Log getAnnotationLog(JoinPoint joinPoint) throws Exception
    {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();

        if (method != null)
        {
            return method.getAnnotation(Log.class);
        }
        return null;
    }

    /**
     * 敏感字段掩码：入参/返回值落库前，把命中键名的值整体替换为 ***。
     * 覆盖口令、验证码、密钥、token 等键（键名精确匹配，大小写不敏感）
     */
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(\"(?:password|oldPassword|newPassword|confirmPassword|pwd|checkCode|smsCode|verifyCode|authCode|code"
                    + "|accessKey|accessKeySecret|secretKey|secret|accessSecret|token|accessToken|refreshToken"
                    + "|access_token|authorization|identityToken)\"\\s*:\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|\\d+)",
            Pattern.CASE_INSENSITIVE);

    private String maskSensitive(String json)
    {
        if (StringUtils.isEmpty(json))
        {
            return json;
        }
        return SENSITIVE_KEY_PATTERN.matcher(json).replaceAll("$1\"***\"");
    }

    /**
     * 参数拼装
     */
    private String argsArrayToString(Object[] paramsArray)
    {
        String params = "";
        if (paramsArray != null && paramsArray.length > 0)
        {
            for (int i = 0; i < paramsArray.length; i++)
            {
                if (StringUtils.isNotNull(paramsArray[i]) && !isFilterObject(paramsArray[i]))
                {
                    try
                    {
                        Object jsonObj = JSON.toJSON(paramsArray[i]);
                        params += jsonObj.toString() + " ";
                    }
                    catch (Exception e)
                    {
                    }
                }
            }
        }
        return params.trim();
    }

    /**
     * 判断是否需要过滤的对象。
     *
     * @param o 对象信息。
     * @return 如果是需要过滤的对象，则返回true；否则返回false。
     */
    @SuppressWarnings("rawtypes")
    public boolean isFilterObject(final Object o)
    {
        Class<?> clazz = o.getClass();
        if (clazz.isArray())
        {
            return clazz.getComponentType().isAssignableFrom(MultipartFile.class);
        }
        else if (Collection.class.isAssignableFrom(clazz))
        {
            Collection collection = (Collection) o;
            for (Iterator iter = collection.iterator(); iter.hasNext();)
            {
                return iter.next() instanceof MultipartFile;
            }
        }
        else if (Map.class.isAssignableFrom(clazz))
        {
            Map map = (Map) o;
            for (Iterator iter = map.entrySet().iterator(); iter.hasNext();)
            {
                Map.Entry entry = (Map.Entry) iter.next();
                return entry.getValue() instanceof MultipartFile;
            }
        }
        return o instanceof MultipartFile || o instanceof HttpServletRequest || o instanceof HttpServletResponse
                || o instanceof BindingResult;
    }
}
