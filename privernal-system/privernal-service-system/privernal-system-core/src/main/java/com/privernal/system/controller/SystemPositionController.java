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
import com.privernal.system.domain.SystemPosition;
import com.privernal.system.dto.SystemPositionDTO;
import com.privernal.system.dto.SystemUserPositionDTO;
import com.privernal.system.filter.SystemPositionFilterDTO;
import com.privernal.system.filter.SystemUserPositionFilterDTO;
import com.privernal.system.service.ISystemPositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 岗位Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 岗位的接口
 */
@Tag(name = "岗位相关接口")
@RestController
public class SystemPositionController extends BaseController {
    @Autowired
    private ISystemPositionService systemPositionService;

    /**
     * 查询岗位列表
     */
    @Operation(summary = "查询岗位列表", description = "查询岗位列表")
    @PreAuthorize(hasPermi = "system:position:list")
    @PostMapping("/back/system/position/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemPositionFilterDTO systemPosition) {
        startPage(systemPosition);
        List<SystemPosition> list = systemPositionService.selectSystemPositionList(systemPosition);
        List<SystemPositionDTO> dtoList = new ArrayList<>();
        for (SystemPosition obj : list) {
            SystemPositionDTO dto = new SystemPositionDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取岗位详细信息
     */
    @Operation(summary = "获取岗位详细信息", description = "获取岗位详细信息")
    @PreAuthorize(hasPermi = "system:position:query")
    @GetMapping(value = "/back/system/position/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemPositionService.selectSystemPositionById(id));
    }

    /**
     * 新增岗位
     */
    @Operation(summary = "新增岗位", description = "新增岗位")
    @PreAuthorize(hasPermi = "system:position:add")
    @Log(title = "岗位", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/position/save")
    public CommonResult<Object> add(@RequestBody SystemPositionDTO systemPositionDTO) {
        SystemPosition systemPosition = new SystemPosition();
        BeanUtils.copyProperties(systemPositionDTO, systemPosition);
        return toAjax(systemPositionService.insertSystemPosition(systemPosition));
    }

    /**
     * 修改岗位
     */
    @Operation(summary = "修改岗位", description = "修改岗位")
    @PreAuthorize(hasPermi = "system:position:edit")
    @Log(title = "岗位", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/position/edit")
    public CommonResult<Object> edit(@RequestBody SystemPositionDTO systemPositionDTO) {
        SystemPosition systemPosition = new SystemPosition();
        BeanUtils.copyProperties(systemPositionDTO, systemPosition);
        return toAjax(systemPositionService.updateSystemPosition(systemPosition));
    }

    /**
     * 删除岗位
     */
    @Operation(summary = "删除岗位", description = "删除岗位")
    @PreAuthorize(hasPermi = "system:position:remove")
    @Log(title = "岗位", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/position/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemPositionService.deleteSystemPositionByIds(ids));
    }

    /**
     * 查询岗位里的用户
     */
    @Operation(summary = "查询岗位里的用户", description = "查询岗位里的用户")
    @PreAuthorize(hasPermi = "system:position:list")
    @PostMapping("/back/system/position/user/list")
    public CommonResult<PageListDTO<Object>> listUser(@RequestBody SystemUserPositionFilterDTO positionFilterDTO) {
        startPage(positionFilterDTO);
        List<SystemUserPositionDTO> list = systemPositionService.selectSystemUserByPositionList(positionFilterDTO.getPositionId(), positionFilterDTO.getNickName());
        return getDataTable(list);
    }

}
