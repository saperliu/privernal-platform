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
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.message.domain.SystemMessage;
import com.privernal.message.dto.MessageUserDTO;
import com.privernal.message.dto.SystemMessageDTO;
import com.privernal.message.filter.SystemMessageFilterDTO;
import com.privernal.message.filter.SystemMessageUserFilterDTO;
import com.privernal.message.service.ISystemMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static com.privernal.message.constant.MessageConstant.CREATED_DEFAULT;

/**
 * 系统消息Controller
 *
 * @author saperliu
 * @date 2023-05-30
 * @Description 系统消息的接口
 */
@Tag(name = "系统消息相关接口")
@RestController
public class SystemMessageController extends BaseController {
    @Autowired
    private ISystemMessageService systemMessageService;

    /**
     * 首页公告栏：全员通知（messagePerson=ALL）最近 20 条，登录态即可访问
     */
    @Operation(summary = "查询全员通知列表", description = "首页公告栏展示全员通知，按创建时间倒序")
    @GetMapping("/back/message/systemMessage/bulletin/list")
    public CommonResult<PageListDTO<Object>> bulletinList() {
        List<SystemMessage> list = systemMessageService.selectBulletinMessageList();
        return CommonResult.ok(new PageListDTO<Object>(list, list.size()));
    }

    /**
     * 查询系统消息通知列表
     */
    @Operation(summary = "查询系统消息通知列表", description = "查询系统消息通知列表")
    @PreAuthorize(hasPermi = "message:systemMessage:list")
    @PostMapping("/back/message/systemMessage/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemMessageFilterDTO systemMessage) {
        startPage(systemMessage);
        List<SystemMessageDTO> list = systemMessageService.selectSystemMessageList(systemMessage);
        List<SystemMessageDTO> dtoList = new ArrayList<>();
        for (SystemMessageDTO obj : list) {
            SystemMessageDTO dto = new SystemMessageDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        return getDataTable(dtoList);
    }

    /**
     * 获取系统消息通知详细信息
     */
    @Operation(summary = "获取系统消息通知详细信息", description = "获取系统消息通知详细信息")
    @PreAuthorize(hasPermi = "message:systemMessage:query")
    @GetMapping(value = "/back/message/systemMessage/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemMessageService.selectSystemMessageDetailDTOById(id));
    }

    /**
     * 新增系统消息
     */
    @Operation(summary = "新增系统消息", description = "新增系统消息")
    @PreAuthorize(hasPermi = "message:systemMessage:add")
    @Log(title = "系统消息", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/message/systemMessage/save")
    public CommonResult<Object> add(@RequestBody SystemMessageDTO systemMessageDTO) {
        if (StringUtils.isNotEmpty(SecurityUtils.getNickName())) {
            systemMessageDTO.setCreatedName(SecurityUtils.getNickName());
        } else {
            systemMessageDTO.setCreatedName(CREATED_DEFAULT);
        }
        SystemMessageDTO newSystemMessageDTO = systemMessageService.saveSystemMessage(systemMessageDTO);
        return CommonResult.ok(newSystemMessageDTO);
    }

    /**
     * 修改系统消息通知
     */
    @Operation(summary = "修改系统消息通知", description = "修改系统消息通知")
    @PreAuthorize(hasPermi = "message:systemMessage:edit")
    @Log(title = "系统消息通知", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/message/systemMessage/edit")
    public CommonResult<Object> edit(@RequestBody SystemMessageDTO systemMessageDTO) {
        SystemMessage systemMessage = new SystemMessage();
        BeanUtils.copyProperties(systemMessageDTO, systemMessage);
        return CommonResult.ok(systemMessageService.updateSystemMessage(systemMessage));
    }

    /**
     * 删除系统消息通知
     */
    @Operation(summary = "删除系统消息通知", description = "删除系统消息通知")
    @PreAuthorize(hasPermi = "message:systemMessage:remove")
    @Log(title = "系统消息通知", operationType = OperationType.DELETE)
    @DeleteMapping("/back/message/systemMessage/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemMessageService.deleteSystemMessageByIds(ids));
    }

    /**
     * 查询系统消息列表
     * 用户APP查询自己的消息
     */
    @Operation(summary = "查询系统消息列表", description = "查询系统消息列表")
    @PostMapping("/app/system/systemMessage/user/list")
    public CommonResult<PageListDTO<Object>> messageUserList(@RequestBody SystemMessageUserFilterDTO filterDTO) {
        startPage(filterDTO);
        filterDTO.setUserId(SecurityUtils.getUserId());
        filterDTO.setOrganizationId(SecurityUtils.getOrganizationId());
        filterDTO.setDepartmentId(SecurityUtils.getDepartmentId());
        List<MessageUserDTO> list = systemMessageService.selectUserMessageList(filterDTO);
        return getDataTable(list);
    }

    /**
     * 系统消息已读
     * 用户APP消息已读
     */
    @Operation(summary = "系统消息已读", description = "系统消息已读")
    @PutMapping("/app/system/systemMessage/user/read/{messageId}")
    public CommonResult<Object> messageUserRead(@PathVariable("messageId") String messageId) {
        int result = systemMessageService.saveUserMessageRead(messageId, SecurityUtils.getUserId(), SecurityUtils.getNickName());
        return CommonResult.ok(result);
    }

    /**
     * 系统消息标记全读
     * 用户APP标记全读
     */
    @Operation(summary = "系统消息标记全读", description = "系统消息标记全读")
    @PutMapping("/app/system/systemMessage/user/readAll")
    public CommonResult<Object> messageUserRead() {
        int result = systemMessageService.saveUserMessageAllRead(SecurityUtils.getUserId(), SecurityUtils.getNickName());
        return CommonResult.ok(result);
    }
}
