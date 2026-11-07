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

package com.privernal.dynamic.datav.controller;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.datav.domain.DynamicDatavFile;
import com.privernal.dynamic.datav.service.DatavDataSourceResolver;
import com.privernal.dynamic.datav.service.IDynamicDatavFileService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV 上传数据源接口
 * <p>
 * 路径挂在数据集下面（/back/report/dataset/csv/*）而不另开一个权限面：文件是 CSV 数据源的
 * 一种取数形态，能力边界应当与「能不能配这个数据源」一致，多一套权限码只是多一处漏配的地方。
 *
 * @author saperliu
 */
@Tag(name = "CSV 上传数据源相关接口")
@RestController
@Slf4j
@RequestMapping("/back/report/dataset/csv")
public class DynamicDatavFileController extends BaseController {

    private static final String SOURCE_TYPE_CSV = "csv";

    private static final String STORAGE_UPLOAD = "upload";

    @Autowired
    private IDynamicDatavFileService datavFileService;

    @Autowired
    private DatavDataSourceResolver datavDataSourceResolver;

    /**
     * 上传 CSV 并顺带抽样列快照。
     * <p>
     * 抽样参数由表单一起提交，否则设计器里选的分隔符/编码用保存时才第一次生效——
     * 「测试连接说行、保存后列对不上」就是这么来的。抽样失败不影响文件已落库：
     * 保存数据集时还会真抽一次，这里只负责让设计器先看见字段。
     */
    @Operation(summary = "上传 CSV 数据源文件", description = "返回 fileId 与抽样列，供设计器在保存前展示字段")
    @PreAuthorize(hasPermi = "report:dataset:add")
    @Log(title = "报表数据集", operationType = OperationType.INSERT)
    @PostMapping("/upload")
    public CommonResult<Object> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "datasetCode", required = false) String datasetCode,
                                       @RequestParam(value = "delimiter", required = false) String delimiter,
                                       @RequestParam(value = "charset", required = false) String charset,
                                       @RequestParam(value = "headerRow", required = false) Boolean headerRow) {
        DynamicDatavFile stored = datavFileService.store(file, datasetCode);
        JSONObject result = new JSONObject();
        result.put("fileId", stored.getFileCode());
        result.put("fileName", stored.getFileName());
        result.put("size", stored.getSizeBytes());

        JSONObject previewConfig = new JSONObject();
        previewConfig.put("storage", STORAGE_UPLOAD);
        previewConfig.put("fileId", stored.getFileCode());
        if (StringUtils.isNotBlank(delimiter)) {
            previewConfig.put("delimiter", delimiter);
        }
        if (StringUtils.isNotBlank(charset)) {
            previewConfig.put("charset", charset);
        }
        if (headerRow != null) {
            previewConfig.put("headerRow", headerRow);
        }
        try {
            JSONArray columns = datavDataSourceResolver.sampleColumns(SOURCE_TYPE_CSV, previewConfig)
                    .getJSONArray("columns");
            result.put("columns", columns);
            result.put("columnCount", columns == null ? 0 : columns.size());
        } catch (ServiceException e) {
            // 只回异常文案，不回堆栈也不回文件名之外的任何内容信息
            result.put("sampleMessage", e.getMessage());
        }
        return CommonResult.ok(result);
    }
}
