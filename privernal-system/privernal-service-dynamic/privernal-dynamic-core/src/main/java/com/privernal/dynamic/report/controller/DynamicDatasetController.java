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

package com.privernal.dynamic.report.controller;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.datav.service.DatavDataSourceResolver;
import com.privernal.dynamic.datav.service.connector.DatavSourceSecrets;
import com.privernal.system.api.ISecretCipherService;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.report.domain.DynamicDataset;
import com.privernal.dynamic.report.service.IDynamicDatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson2.JSONObject;
import java.util.List;
import java.util.Map;

/**
 * 报表数据集 Controller
 *
 * @author saperliu
 */
@Tag(name = "报表数据集相关接口")
@RestController
@Slf4j
@RequestMapping("/back/report/dataset")
public class DynamicDatasetController extends BaseController {

    @Autowired
    private IDynamicDatasetService datasetService;

    @Autowired
    private DatavDataSourceResolver datavDataSourceResolver;

    @Autowired
    private ISecretCipherService secretCipher;

    /**
     * 保存数据集（新增/按编码更新，列快照随保存解析）
     */
    @Operation(summary = "保存数据集")
    @PreAuthorize(hasPermi = "report:dataset:add")
    @Log(title = "报表数据集", operationType = OperationType.UPDATE)
    @PostMapping("/save")
    public CommonResult<Object> save(@RequestBody DynamicDataset dataset) {
        return CommonResult.ok(masked(datasetService.saveDynamicDataset(dataset)));
    }

    /**
     * 刷新列快照
     */
    @Operation(summary = "刷新列快照", description = "按底层表单当前列重建快照；字段被图表引用且已删除时明确报错")
    @PreAuthorize(hasPermi = "report:dataset:edit")
    @Log(title = "报表数据集", operationType = OperationType.UPDATE)
    @PutMapping("/refresh/{datasetCode}")
    public CommonResult<Object> refresh(@PathVariable("datasetCode") String datasetCode) {
        return CommonResult.ok(masked(datasetService.refreshDataset(datasetCode)));
    }

    /**
     * 数据源健康检查（M3.9）：按提交的配置真实访问一次，返回 {ok, category, message, columns}。
     * <p>
     * 编辑态表单里的凭据是掩码，必须先按库里已有的配置还原再发请求——否则等于把
     * 字面量 "********" 当密码送出去，检查结果必然失败，而且原因完全误导人。
     * 新建（无 datasetCode）时掩码项会被直接丢弃，即「以无凭据方式试探」，同样不会外泄掩码。
     */
    @Operation(summary = "数据源健康检查", description = "真实访问一次数据源，返回可达性、错误分类与抽样列")
    @PreAuthorize(hasPermi = "report:dataset:add")
    @PostMapping("/health")
    public CommonResult<Object> health(@RequestBody Map<String, Object> body) {
        String sourceType = StringUtils.trimToEmpty((String) body.get("sourceType"));
        Object rawConfig = body.get("sourceConfig");
        // Spring 用 Jackson 绑定 Map，嵌套对象是 LinkedHashMap，必须显式转成 fastjson2
        JSONObject sourceConfig = rawConfig == null ? new JSONObject() : JSONObject.from(rawConfig);
        String datasetCode = StringUtils.trimToEmpty((String) body.get("datasetCode"));
        DynamicDataset exists = StringUtils.isBlank(datasetCode) ? null : datasetService.findByCode(datasetCode);
        JSONObject sealed = DatavSourceSecrets.seal(sourceConfig,
                exists == null ? null : exists.getSourceConfig(), secretCipher);
        return CommonResult.ok(datavDataSourceResolver.health(sourceType, sealed));
    }

    /**
     * 出口统一掩码：存进去的是可逆密文，把它原样回传有三个害处——密文与主密钥同流即等于明文、
     * 前端下次保存会把密文再封一层（解不开、且只在取数时才表现为「数据源连不上」），
     * 以及任何人从接口响应就能判断某个源到底配没配凭据。
     */
    private DynamicDataset masked(DynamicDataset dataset) {
        if (dataset != null) {
            dataset.setSourceConfig(DatavSourceSecrets.mask(dataset.getSourceConfig()));
        }
        return dataset;
    }

    /**
     * 数据集列表（按应用）
     */
    @Operation(summary = "数据集列表")
    @PreAuthorize(hasPermi = "report:dataset:list")
    @GetMapping("/list")
    public CommonResult<Object> list(@RequestParam("appId") String appId) {
        List<DynamicDataset> list = datasetService.listByApp(appId);
        // 凭据不回显：这两个接口都是喂给设计器的，明文与密文都不该出现在响应里（M3.3）
        list.forEach(item -> item.setSourceConfig(DatavSourceSecrets.mask(item.getSourceConfig())));
        return CommonResult.ok(list);
    }

    /**
     * 数据集详情
     */
    @Operation(summary = "数据集详情")
    @PreAuthorize(hasPermi = "report:dataset:query")
    @GetMapping("/{datasetCode}")
    public CommonResult<Object> detail(@PathVariable("datasetCode") String datasetCode) {
        DynamicDataset dataset = datasetService.findByCode(datasetCode);
        if (dataset != null) {
            dataset.setSourceConfig(DatavSourceSecrets.mask(dataset.getSourceConfig()));
        }
        return CommonResult.ok(dataset);
    }

    /**
     * 删除数据集
     */
    @Operation(summary = "删除数据集")
    @PreAuthorize(hasPermi = "report:dataset:remove")
    @Log(title = "报表数据集", operationType = OperationType.DELETE)
    @DeleteMapping("/{datasetCode}")
    public CommonResult<Object> remove(@PathVariable("datasetCode") String datasetCode) {
        if (StringUtils.isBlank(datasetCode)) {
            return CommonResult.fail("数据集编码不能为空");
        }
        return toAjax(datasetService.deleteByCode(datasetCode));
    }
}
