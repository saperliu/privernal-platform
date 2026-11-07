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

package com.privernal.common.security.handler;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.exception.DemoModeException;
import com.privernal.common.core.exception.PasswordMigrateRequiredException;
import com.privernal.common.core.exception.PreAuthorizeException;
import com.privernal.common.core.exception.RateLimiterException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.web.domain.CommonResult;
import org.mybatis.spring.MyBatisSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 处理器
 *
 * @author saperliu
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public CommonResult<Object> businessException(ServiceException e) {
        if (StringUtils.isNull(e.getCode())) {
            return CommonResult.fail(e.getMessage());
        }
        return CommonResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public CommonResult<Object> handleException(Exception e) {
        log.error("GlobalExceptionHandler   {}   {} ", e.getMessage(), e);
        // 不同异常返回不同状态码
        CommonResult<Object> aj = null;
        if (e instanceof NotLoginException) {    // 如果是未登录异常
            NotLoginException ee = (NotLoginException) e;
            aj = CommonResult.fail(ee.getMessage());
        } else if (e instanceof NotRoleException) {        // 如果是角色异常
            NotRoleException ee = (NotRoleException) e;
            aj = CommonResult.fail("无此角色：" + ee.getRole());
        } else if (e instanceof NotPermissionException) {    // 如果是权限异常
            NotPermissionException ee = (NotPermissionException) e;
            aj = CommonResult.fail("无此权限：" + ee.getCode());
        } else if (e instanceof DisableServiceException) {    // 如果是被封禁异常
            DisableServiceException ee = (DisableServiceException) e;
            aj = CommonResult.fail("账号被封禁：" + ee.getDisableTime() + "秒后解封");
        } else if (e instanceof MyBatisSystemException) {    // 数据库异常：细节只进日志，不回吐 SQL 片段（S18）
            log.error("数据库查询异常", e);
            aj = CommonResult.fail("数据库查询错误，请稍后重试或联系管理员");
        } else if (e instanceof DuplicateKeyException) {    // 唯一键冲突（如表单编码被已有/已删除数据占用）
            aj = CommonResult.fail("保存失败：数据编码已存在（可能被已删除的历史数据占用），请更换编码后重试");
        } else {    // 普通异常, 输出：500 + 异常信息
            aj = CommonResult.fail(e.getMessage());
        }
        // 返回给前端
        return aj;
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(BindException.class)
    public CommonResult<Object> validatedBindException(BindException e) {
        log.error(e.getMessage(), e);
        String message = e.getAllErrors().get(0).getDefaultMessage();
        return CommonResult.fail(message);
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Object> validExceptionHandler(MethodArgumentNotValidException e) {
        log.error(e.getMessage(), e);
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        return CommonResult.fail(message);
    }

    /**
     * 权限异常
     */
    @ExceptionHandler(PreAuthorizeException.class)
    public CommonResult<Object> preAuthorizeException(PreAuthorizeException e) {
        return CommonResult.fail("没有权限，请联系管理员授权");
    }

    /**
     * 接口限流异常（@RateLimiter 触发）
     */
    @ExceptionHandler(RateLimiterException.class)
    public CommonResult<Object> rateLimiterException(RateLimiterException e) {
        return CommonResult.fail("请求过于频繁，请稍后重试");
    }

    /**
     * F4 口令凭证待迁移：返回专用码，客户端自动降级引导一次后重试挑战登录
     */
    @ExceptionHandler(PasswordMigrateRequiredException.class)
    public CommonResult<Object> passwordMigrateRequiredException(PasswordMigrateRequiredException e) {
        return CommonResult.fail(Constants.CODE_PWD_MIGRATE_REQUIRED, "口令凭证待迁移");
    }

    /**
     * 演示模式异常
     */
    @ExceptionHandler(DemoModeException.class)
    public CommonResult<Object> demoModeException(DemoModeException e) {
        return CommonResult.fail("演示模式，不允许操作");
    }


    // 拦截（拦截项目中的所有异常）
    @ResponseBody
    @ExceptionHandler(NotLoginException.class)
    public CommonResult<Object> handlerNotLoginException(NotLoginException nle, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        log.error(" global exception  ", nle);
        // 判断场景值，定制化异常信息
        String message = "";
        switch (nle.getType()) {
            case NotLoginException.NOT_TOKEN:
                message = "未提供token";
                break;
            case NotLoginException.INVALID_TOKEN:
                message = "token无效";
                break;
            case NotLoginException.TOKEN_TIMEOUT:
                message = "token已过期";
                break;
            case NotLoginException.BE_REPLACED:
                message = "token已被顶下线";
                break;
            case NotLoginException.KICK_OUT:
                message = "token已被踢下线";
                break;
            default:
                message = "当前会话未登录";
                break;
        }
        response.addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        // 返回给前端
        return CommonResult.fail(message);
    }
}
