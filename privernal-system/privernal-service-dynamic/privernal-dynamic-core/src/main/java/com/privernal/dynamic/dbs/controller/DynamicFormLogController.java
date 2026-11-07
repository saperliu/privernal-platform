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
import com.privernal.dynamic.dbs.domain.DynamicFormLog;
import com.privernal.dynamic.dbs.service.IDynamicFormLogService;
import com.privernal.dynamic.dto.DynamicFormLogDTO;
import com.privernal.dynamic.filter.DynamicFormLogFilterDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * form的历史版本Controller
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description form的历史版本的接口
 */
@Tag(name = "form的历史版本相关接口")
@RestController
@Slf4j
public class DynamicFormLogController extends BaseController {
    @Autowired
    private IDynamicFormLogService dynamicFormLogService;

    /**
     * 查询form的历史版本列表
     */
    @Operation(summary = "查询form的历史版本列表", description = "查询form的历史版本列表")
    @PreAuthorize(hasPermi = "dynamic:formLog:list")
    @PostMapping("/back/dynamic/formLog/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody DynamicFormLogFilterDTO dynamicFormLog) {
        startPage(dynamicFormLog);
        List<DynamicFormLogDTO> list = dynamicFormLogService.selectDynamicFormLogList(dynamicFormLog);
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        return tableResult;
    }

    /**
     * 获取form的历史版本详细信息
     */
    @Operation(summary = "获取form的历史版本详细信息", description = "获取form的历史版本详细信息")
    @PreAuthorize(hasPermi = "dynamic:formLog:query")
    @GetMapping(value = "/back/dynamic/formLog/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(dynamicFormLogService.selectDynamicFormLogDetailDTOById(id));
    }

    /**
     * 新增form的历史版本
     */
    @Operation(summary = "新增form的历史版本", description = "新增form的历史版本")
    @PreAuthorize(hasPermi = "dynamic:formLog:add")
    @Log(title = "form的历史版本", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/dynamic/formLog/save")
    public CommonResult<Object> add(@RequestBody DynamicFormLogDTO dynamicFormLogDTO) {
        DynamicFormLog dynamicFormLog = new DynamicFormLog();
        BeanUtils.copyProperties(dynamicFormLogDTO, dynamicFormLog, DataConstants.IGNORED_ON_COPY);
        return CommonResult.ok(dynamicFormLogService.insertDynamicFormLog(dynamicFormLog));
    }

    /**
     * 修改form的历史版本
     */
    @Operation(summary = "修改form的历史版本", description = "修改form的历史版本")
    @PreAuthorize(hasPermi = "dynamic:formLog:edit")
    @Log(title = "form的历史版本", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/dynamic/formLog/edit")
    public CommonResult<Object> edit(@RequestBody DynamicFormLogDTO dynamicFormLogDTO) {
        DynamicFormLog dynamicFormLog = new DynamicFormLog();
        BeanUtils.copyProperties(dynamicFormLogDTO, dynamicFormLog, DataConstants.IGNORED_ON_COPY);
        return CommonResult.ok(dynamicFormLogService.updateDynamicFormLog(dynamicFormLog));
    }

    /**
     * 删除form的历史版本
     */
    @Operation(summary = "删除form的历史版本", description = "删除form的历史版本")
    @PreAuthorize(hasPermi = "dynamic:formLog:remove")
    @Log(title = "form的历史版本", operationType = OperationType.DELETE)
    @DeleteMapping("/back/dynamic/formLog/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(dynamicFormLogService.deleteDynamicFormLogByIds(ids));
    }
}
