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

package com.privernal.message.controller;

import com.privernal.common.core.constant.DataConstants;
import org.springframework.beans.BeanUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.message.domain.SystemMessageUser;
import com.privernal.message.dto.SystemMessageUserDTO;
import com.privernal.message.filter.SystemMessageUserFilterDTO;
import com.privernal.message.service.ISystemMessageUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统消息的用户Controller
 *
 * @author saperliu
 * @date 2023-05-30
 * @Description 系统消息的用户的接口
 */
@Tag(name = "系统消息的用户相关接口")
@RestController
public class SystemMessageUserController extends BaseController {
    @Autowired
    private ISystemMessageUserService systemMessageUserService;

    /**
     * 查询系统通知人员列表
     */
    @Operation(summary = "查询系统通知人员列表", description = "查询系统通知人员列表")
    @PreAuthorize(hasPermi = "message:systemMessageUser:list")
    @PostMapping("/back/message/systemMessageUser/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemMessageUserFilterDTO systemMessageUser) {
        startPage(systemMessageUser);
        List<SystemMessageUserDTO> list = systemMessageUserService.selectSystemMessageUserList(systemMessageUser);
        List<SystemMessageUserDTO> dtoList = new ArrayList<>();
        for (SystemMessageUserDTO obj : list) {
            SystemMessageUserDTO dto = new SystemMessageUserDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        return getDataTable(dtoList);
    }

    /**
     * 获取系统通知人员详细信息
     */
    @Operation(summary = "获取系统通知人员详细信息", description = "获取系统通知人员详细信息")
    @PreAuthorize(hasPermi = "message:systemMessageUser:query")
    @GetMapping(value = "/back/message/systemMessageUser/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemMessageUserService.selectSystemMessageUserDetailDTOById(id));
    }

    /**
     * 新增系统通知人员
     */
    @Operation(summary = "新增系统通知人员", description = "新增系统通知人员")
    @PreAuthorize(hasPermi = "message:systemMessageUser:add")
    @Log(title = "系统通知人员", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/message/systemMessageUser/save")
    public CommonResult<Object> add(@RequestBody SystemMessageUserDTO systemMessageUserDTO) {
        SystemMessageUser systemMessageUser = new SystemMessageUser();
        BeanUtils.copyProperties(systemMessageUserDTO, systemMessageUser);
        return CommonResult.ok(systemMessageUserService.insertSystemMessageUser(systemMessageUser));
    }

    /**
     * 修改系统通知人员
     */
    @Operation(summary = "修改系统通知人员", description = "修改系统通知人员")
    @PreAuthorize(hasPermi = "message:systemMessageUser:edit")
    @Log(title = "系统通知人员", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/message/systemMessageUser/edit")
    public CommonResult<Object> edit(@RequestBody SystemMessageUserDTO systemMessageUserDTO) {
        SystemMessageUser systemMessageUser = new SystemMessageUser();
        BeanUtils.copyProperties(systemMessageUserDTO, systemMessageUser);
        return CommonResult.ok(systemMessageUserService.updateSystemMessageUser(systemMessageUser));
    }

    /**
     * 删除系统通知人员
     */
    @Operation(summary = "删除系统通知人员", description = "删除系统通知人员")
    @PreAuthorize(hasPermi = "message:systemMessageUser:remove")
    @Log(title = "系统通知人员", operationType = OperationType.DELETE)
    @DeleteMapping("/back/message/systemMessageUser/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemMessageUserService.deleteSystemMessageUserByIds(ids));
    }
}
