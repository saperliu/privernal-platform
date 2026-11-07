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
import com.privernal.system.domain.SystemCommonGroup;
import com.privernal.system.dto.SystemCommonGroupDTO;
import com.privernal.system.filter.SystemCommonGroupFilterDTO;
import com.privernal.system.service.ISystemCommonGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 公共组[用户或角色]Controller
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 公共组[用户或角色]的接口
 */
@Tag(name = "公共组[用户或角色]相关接口")
@RestController
@Slf4j
public class SystemCommonGroupController extends BaseController {
    @Autowired
    private ISystemCommonGroupService systemCommonGroupService;

    /**
     * 查询公共组[用户或角色]列表
     */
    @Operation(summary = "查询公共组[用户或角色]列表", description = "查询公共组[用户或角色]列表")
    @PreAuthorize(hasPermi = "system:commonGroup:list")
    @PostMapping("/back/system/commonGroup/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemCommonGroupFilterDTO systemCommonGroup) {
        startPage(systemCommonGroup);
        List<SystemCommonGroupDTO> list = systemCommonGroupService.selectSystemCommonGroupList(systemCommonGroup);
        List<SystemCommonGroupDTO> dtoList = new ArrayList<>();
        for (SystemCommonGroupDTO obj : list) {
            SystemCommonGroupDTO dto = new SystemCommonGroupDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        return getDataTable(dtoList);
    }

    /**
     * 获取公共组[用户或角色]详细信息
     */
    @Operation(summary = "获取公共组[用户或角色]详细信息", description = "获取公共组[用户或角色]详细信息")
    @PreAuthorize(hasPermi = "system:commonGroup:query")
    @GetMapping(value = "/back/system/commonGroup/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemCommonGroupService.selectSystemCommonGroupDetailDTOById(id));
    }

    /**
     * 新增公共组[用户或角色]
     */
    @Operation(summary = "新增公共组[用户或角色]", description = "新增公共组[用户或角色]")
    @PreAuthorize(hasPermi = "system:commonGroup:add")
    @Log(title = "公共组[用户或角色]", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/commonGroup/save")
    public CommonResult<Object> add(@RequestBody SystemCommonGroupDTO systemCommonGroupDTO) {
//        SystemCommonGroup systemCommonGroup = new SystemCommonGroup();
//        BeanUtils.copyProperties(systemCommonGroupDTO, systemCommonGroup);
        return CommonResult.ok(systemCommonGroupService.saveSystemCommonGroupDTO(systemCommonGroupDTO));
    }

    /**
     * 修改公共组[用户或角色]
     */
    @Operation(summary = "修改公共组[用户或角色]", description = "修改公共组[用户或角色]")
    @PreAuthorize(hasPermi = "system:commonGroup:edit")
    @Log(title = "公共组[用户或角色]", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/commonGroup/edit")
    public CommonResult<Object> edit(@RequestBody SystemCommonGroupDTO systemCommonGroupDTO) {
//        SystemCommonGroup systemCommonGroup = new SystemCommonGroup();
//        BeanUtils.copyProperties(systemCommonGroupDTO, systemCommonGroup);
        return CommonResult.ok(systemCommonGroupService.saveSystemCommonGroupDTO(systemCommonGroupDTO));
    }

    /**
     * 删除公共组[用户或角色]
     */
    @Operation(summary = "删除公共组[用户或角色]", description = "删除公共组[用户或角色]")
    @PreAuthorize(hasPermi = "system:commonGroup:remove")
    @Log(title = "公共组[用户或角色]", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/commonGroup/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemCommonGroupService.deleteSystemCommonGroupByIds(ids));
    }
}
