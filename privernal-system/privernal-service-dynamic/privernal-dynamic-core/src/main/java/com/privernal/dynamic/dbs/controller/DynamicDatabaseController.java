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

package com.privernal.dynamic.dbs.controller;

import com.privernal.common.core.constant.DataConstants;
import org.springframework.beans.BeanUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.dynamic.dbs.domain.DynamicDatabase;
import com.privernal.dynamic.dbs.service.IDynamicDatabaseService;
import com.privernal.dynamic.dto.DynamicDatabaseDTO;
import com.privernal.dynamic.filter.DynamicDatabaseFilterDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 动态数据库Controller
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description 动态数据库的接口
 */
@Tag(name = "动态数据库相关接口")
@RestController
@Slf4j
public class DynamicDatabaseController extends BaseController {
    @Autowired
    private IDynamicDatabaseService dynamicDatabaseService;

    /**
     * 查询动态数据库列表
     */
    @Operation(summary = "查询动态数据库列表", description = "查询动态数据库列表")
    @PreAuthorize(hasPermi = "dynamic:database:list")
    @PostMapping("/back/dynamic/database/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody DynamicDatabaseFilterDTO dynamicDatabase) {
        startPage(dynamicDatabase);
        List<DynamicDatabaseDTO> list = dynamicDatabaseService.selectDynamicDatabaseList(dynamicDatabase);
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        return tableResult;
    }

    /**
     * 获取动态数据库详细信息
     */
    @Operation(summary = "获取动态数据库详细信息", description = "获取动态数据库详细信息")
    @PreAuthorize(hasPermi = "dynamic:database:query")
    @GetMapping(value = "/back/dynamic/database/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(dynamicDatabaseService.selectDynamicDatabaseDetailDTOById(id));
    }

    /**
     * 新增动态数据库
     */
    @Operation(summary = "新增动态数据库", description = "新增动态数据库")
    @PreAuthorize(hasPermi = "dynamic:database:add")
    @Log(title = "动态数据库", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/dynamic/database/save")
    public CommonResult<Object> add(@RequestBody DynamicDatabaseDTO dynamicDatabaseDTO) {
        DynamicDatabase dynamicDatabase = new DynamicDatabase();
        BeanUtils.copyProperties(dynamicDatabaseDTO, dynamicDatabase, DataConstants.IGNORED_ON_COPY);
        return CommonResult.ok(dynamicDatabaseService.insertDynamicDatabase(dynamicDatabase));
    }

    /**
     * 修改动态数据库
     */
    @Operation(summary = "修改动态数据库", description = "修改动态数据库")
    @PreAuthorize(hasPermi = "dynamic:database:edit")
    @Log(title = "动态数据库", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/dynamic/database/edit")
    public CommonResult<Object> edit(@RequestBody DynamicDatabaseDTO dynamicDatabaseDTO) {
        DynamicDatabase dynamicDatabase = new DynamicDatabase();
        BeanUtils.copyProperties(dynamicDatabaseDTO, dynamicDatabase, DataConstants.IGNORED_ON_COPY);
        return CommonResult.ok(dynamicDatabaseService.updateDynamicDatabase(dynamicDatabase));
    }

    /**
     * 删除动态数据库
     */
    @Operation(summary = "删除动态数据库", description = "删除动态数据库")
    @PreAuthorize(hasPermi = "dynamic:database:remove")
    @Log(title = "动态数据库", operationType = OperationType.DELETE)
    @DeleteMapping("/back/dynamic/database/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(dynamicDatabaseService.deleteDynamicDatabaseByIds(ids));
    }
}
