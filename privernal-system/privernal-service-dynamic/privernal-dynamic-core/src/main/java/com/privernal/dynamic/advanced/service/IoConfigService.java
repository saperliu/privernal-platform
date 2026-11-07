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

package com.privernal.dynamic.advanced.service;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.advanced.domain.DynamicFormIoConfig;
import com.privernal.dynamic.advanced.mapper.DynamicFormIoConfigMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 导入导出配置服务（§4.3）：一表单一行（uk_form），首次读取时惰性落默认行。
 * 运行时导入导出动作本体为 P1（§9），本服务先行提供配置面板的读写。
 */
@Service
public class IoConfigService {

    @Autowired
    private DynamicFormIoConfigMapper ioConfigMapper;

    public DynamicFormIoConfig getByFormId(String formId) {
        DynamicFormIoConfig config = ioConfigMapper.selectOne(Wrappers.<DynamicFormIoConfig>lambdaQuery()
                .eq(DynamicFormIoConfig::getFormId, formId));
        if (config == null) {
            config = new DynamicFormIoConfig();
            config.setFormId(formId);
            config.setExportEnabled("N");
            config.setImportEnabled("N");
            config.setExportScope("SELF");
            config.setSensitivePolicy("MASK");
            config.setDupStrategy("SKIP");
            config.setStrictValidate("Y");
            config.setImportLimit(5000);
            config.setImportAsync("Y");
            config.setRunBackscript("N");
            config.setTriggerMode("SUPPRESS");
            config.setImportFlowMode("NONE");
        }
        return config;
    }

    @Transactional(rollbackFor = Exception.class)
    public DynamicFormIoConfig save(DynamicFormIoConfig config) {
        if (StringUtils.isEmpty(config.getFormId())) {
            throw new IllegalArgumentException("formId 不能为空");
        }
        DynamicFormIoConfig existing = ioConfigMapper.selectOne(Wrappers.<DynamicFormIoConfig>lambdaQuery()
                .eq(DynamicFormIoConfig::getFormId, config.getFormId()));
        if (existing == null) {
            config.setId(null);
            // department_id/organization_id 无自动填充，缺省时取会话归属兜底
            if (StringUtils.isEmpty(config.getOrganizationId())) {
                config.setOrganizationId(com.privernal.common.security.service.SecurityUtils.getOrganizationId());
            }
            if (StringUtils.isEmpty(config.getDepartmentId())) {
                config.setDepartmentId(com.privernal.common.security.service.SecurityUtils.getDepartmentId());
            }
            ioConfigMapper.insert(config);
            return config;
        }
        config.setId(existing.getId());
        ioConfigMapper.updateById(config);
        return config;
    }
}
