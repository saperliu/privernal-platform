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

package com.privernal.system.controller;

import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import org.springframework.beans.BeanUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemNotice;
import com.privernal.system.dto.SystemNoticeDTO;
import com.privernal.system.filter.SystemNoticeFilterDTO;
import com.privernal.system.service.ISystemNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 通知的接口
 */
@Tag(name = "通知相关接口")
@RestController
public class SystemNoticeController extends BaseController {
    @Autowired
    private ISystemNoticeService systemNoticeService;

    /**
     * 查询通知列表
     */
    @Operation(summary = "查询通知列表", description = "查询通知列表")
    @PreAuthorize(hasPermi = "system:notice:list")
    @PostMapping("/back/system/notice/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemNoticeFilterDTO systemNotice) {
        startPage(systemNotice);
        List<SystemNotice> list = systemNoticeService.selectSystemNoticeList(systemNotice);
        List<SystemNoticeDTO> dtoList = new ArrayList<>();
        for (SystemNotice obj : list) {
            SystemNoticeDTO dto = new SystemNoticeDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取通知详细信息
     */
    @Operation(summary = "获取通知详细信息", description = "获取通知详细信息")
    @PreAuthorize(hasPermi = "system:notice:query")
    @GetMapping(value = "/back/system/notice/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemNoticeService.selectSystemNoticeById(id));
    }

    /**
     * 新增通知
     */
    @Operation(summary = "新增通知", description = "新增通知")
    @PreAuthorize(hasPermi = "system:notice:add")
    @Log(title = "通知", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/notice/save")
    public CommonResult<Object> add(@RequestBody SystemNoticeDTO systemNoticeDTO) {
        SystemNotice systemNotice = new SystemNotice();
        BeanUtils.copyProperties(systemNoticeDTO, systemNotice);
        return toAjax(systemNoticeService.insertSystemNotice(systemNotice));
    }

    /**
     * 修改通知
     */
    @Operation(summary = "修改通知", description = "修改通知")
    @PreAuthorize(hasPermi = "system:notice:edit")
    @Log(title = "通知", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/notice/edit")
    public CommonResult<Object> edit(@RequestBody SystemNoticeDTO systemNoticeDTO) {
        SystemNotice systemNotice = new SystemNotice();
        BeanUtils.copyProperties(systemNoticeDTO, systemNotice);
        return toAjax(systemNoticeService.updateSystemNotice(systemNotice));
    }

    /**
     * 删除通知
     */
    @Operation(summary = "删除通知", description = "删除通知")
    @PreAuthorize(hasPermi = "system:notice:remove")
    @Log(title = "通知", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/notice/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemNoticeService.deleteSystemNoticeByIds(ids));
    }

    /**
     * 查询通知列表
     * 首页调用
     */
    @Operation(summary = "首页调用查询公告列表", description = "首页调用查询公告列表")
    @GetMapping("/back/system/notice/bulletin/list")
    public CommonResult<PageListDTO<Object>> listNotice() {
        SystemNoticeFilterDTO systemNotice = new SystemNoticeFilterDTO();
        systemNotice.setPageNum(1);
        systemNotice.setPageSize(20);
        startPage(systemNotice);
        systemNotice.setStatus(WorkflowDataStatus.FINISH.getCode());
        List<SystemNotice> list = systemNoticeService.selectSystemNoticeList(systemNotice);
        List<SystemNoticeDTO> dtoList = new ArrayList<>();
        for (SystemNotice obj : list) {
            SystemNoticeDTO dto = new SystemNoticeDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }
}
