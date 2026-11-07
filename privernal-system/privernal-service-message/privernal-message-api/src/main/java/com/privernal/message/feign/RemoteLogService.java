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

package com.privernal.message.feign;

import com.privernal.common.core.constant.Constants;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.message.dto.SystemLogLoginDTO;
import com.privernal.message.dto.SystemLogOperationDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * @author saperliu
 * @date 2022-04-10
 * @Description 日志服务
 */
public interface RemoteLogService {

    /**
     * 保存访问记录
     *
     * @param systemLogLoginDTO 系统登录日志
     * @param request           请求的类型
     * @return 结果
     */
    @PostMapping(value = "/inner/system/login/save")
    public CommonResult<Object> saveLoginLog(@RequestBody SystemLogLoginDTO systemLogLoginDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 操作日志的存储
     *
     * @param systemLogOperationDTO 系统操作日志
     * @param request               请求的类型
     * @return
     */
    @PostMapping(value = "/inner/system/operation/save")
    public CommonResult<Object> saveOperationLog(@RequestBody SystemLogOperationDTO systemLogOperationDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);
}
