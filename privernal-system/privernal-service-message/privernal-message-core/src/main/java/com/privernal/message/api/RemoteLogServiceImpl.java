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

package com.privernal.message.api;

import org.springframework.beans.BeanUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.message.domain.SystemLogLogin;
import com.privernal.message.domain.SystemLogOperation;
import com.privernal.message.dto.SystemLogLoginDTO;
import com.privernal.message.dto.SystemLogOperationDTO;
import com.privernal.message.feign.RemoteLogService;
import com.privernal.message.service.ISystemLogLoginService;
import com.privernal.message.service.ISystemLogOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RemoteLogServiceImpl implements RemoteLogService {

    @Autowired
    private ISystemLogLoginService systemLogLoginService;

    @Autowired
    private ISystemLogOperationService systemLogOperationService;


    @Override
    public CommonResult<Object> saveLoginLog(SystemLogLoginDTO systemLogLoginDTO, String request) {

        SystemLogLogin systemLogLogin = new SystemLogLogin();
        BeanUtils.copyProperties(systemLogLoginDTO, systemLogLogin);
        return CommonResult.ok(systemLogLoginService.insertSystemLogLogin(systemLogLogin));

    }

    @Override
    public CommonResult<Object> saveOperationLog(SystemLogOperationDTO systemLogOperationDTO, String request) {
        SystemLogOperation systemLogOperation = new SystemLogOperation();
        BeanUtils.copyProperties(systemLogOperationDTO, systemLogOperation);
        return CommonResult.ok(systemLogOperationService.insertSystemLogOperation(systemLogOperation));
    }
}
