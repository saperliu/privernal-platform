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

package com.privernal.auth.controller;


import com.privernal.common.core.exception.CaptchaException;
import com.privernal.common.security.annotation.RateLimiter;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.gateway.service.ValidateCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 登录的接口
 *
 * @author saperliu
 * @date 2022-04-10
 * @Description 验证码的接口
 */
@Slf4j
@RestController
@Tag(name = "登录接口", description = "验证码")
public class ValidateCodeController {

    @Autowired
    private ValidateCodeService validateCodeService;

    /**
     * 获取验证码
     *
     * @return 验证码信息
     */
    @Operation(summary = "忘记密码")
    @RateLimiter(key = "auth-code", max = 30, period = 60)
    @GetMapping("/auth/code")
    @ResponseBody
    public CommonResult<?> getCode() {
        log.info("getCode code：");
        CommonResult<String> ajax;
        try {
            ajax = validateCodeService.createCaptcha();
            Map<String, Object> data = new HashMap<>();
            data.put("data", ajax.getData());
            data.put("id", ajax.getId());
            return CommonResult.ok(data);
        } catch (CaptchaException | IOException e) {
            log.error("", e);
        }
        return CommonResult.fail("验证码错误");
    }

}
