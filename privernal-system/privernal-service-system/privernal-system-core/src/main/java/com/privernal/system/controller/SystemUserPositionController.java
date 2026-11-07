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
import org.springframework.beans.BeanUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.system.domain.SystemUserPosition;
import com.privernal.system.dto.SystemUserPositionDTO;
import com.privernal.system.filter.SystemUserPositionFilterDTO;
import com.privernal.system.service.ISystemUserPositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户的岗位Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户的岗位的接口
 */
@Tag(name = "用户的岗位相关接口")
@RestController
public class SystemUserPositionController extends BaseController {
    @Autowired
    private ISystemUserPositionService systemUserPositionService;

    /**
     * 查询用户的岗位列表
     */
    @Operation(summary = "查询用户的岗位列表", description = "查询用户的岗位列表")
    @PreAuthorize(hasPermi = "system:userPosition:list")
    @PostMapping("/back/system/userPosition/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemUserPositionFilterDTO systemUserPosition) {
        startPage(systemUserPosition);
        List<SystemUserPosition> list = systemUserPositionService.selectSystemUserPositionList(systemUserPosition);
        List<SystemUserPositionDTO> dtoList = new ArrayList<>();
        for (SystemUserPosition obj : list) {
            SystemUserPositionDTO dto = new SystemUserPositionDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取用户的岗位详细信息
     */
    @Operation(summary = "获取用户的岗位详细信息", description = "获取用户的岗位详细信息")
    @PreAuthorize(hasPermi = "system:userPosition:query")
    @GetMapping(value = "/back/system/userPosition/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemUserPositionService.selectSystemUserPositionById(id));
    }

    /**
     * 新增用户的岗位
     */
    @Operation(summary = "新增用户的岗位", description = "新增用户的岗位")
    @PreAuthorize(hasPermi = "system:userPosition:add")
    @Log(title = "用户的岗位", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/userPosition/save")
    public CommonResult<Object> add(@RequestBody SystemUserPositionDTO systemUserPositionDTO) {
        SystemUserPosition systemUserPosition = new SystemUserPosition();
        BeanUtils.copyProperties(systemUserPositionDTO, systemUserPosition);
        return toAjax(systemUserPositionService.insertSystemUserPosition(systemUserPosition));
    }

    /**
     * 修改用户的岗位
     */
    @Operation(summary = "修改用户的岗位", description = "修改用户的岗位")
    @PreAuthorize(hasPermi = "system:userPosition:edit")
    @Log(title = "用户的岗位", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/userPosition/edit")
    public CommonResult<Object> edit(@RequestBody SystemUserPositionDTO systemUserPositionDTO) {
        SystemUserPosition systemUserPosition = new SystemUserPosition();
        BeanUtils.copyProperties(systemUserPositionDTO, systemUserPosition);
        return toAjax(systemUserPositionService.updateSystemUserPosition(systemUserPosition));
    }

    /**
     * 删除用户的岗位
     */
    @Operation(summary = "删除用户的岗位", description = "删除用户的岗位")
    @PreAuthorize(hasPermi = "system:userPosition:remove")
    @Log(title = "用户的岗位", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/userPosition/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemUserPositionService.deleteSystemUserPositionByIds(ids));
    }

    /**
     * 新增岗位与用户关系
     */
    @Operation(summary = "新增岗位与用户关系", description = "新增岗位与用户关系")
    @PreAuthorize(hasPermi = "system:userPosition:add")
    @Log(title = "新增岗位与用户关系", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/userPosition/batch/save")
    public CommonResult<Object> addBatch(@RequestBody SystemUserPositionDTO systemUserPositionDTO) {
        return toAjax(systemUserPositionService.insertBatchSystemUserPosition(systemUserPositionDTO));
    }

}
