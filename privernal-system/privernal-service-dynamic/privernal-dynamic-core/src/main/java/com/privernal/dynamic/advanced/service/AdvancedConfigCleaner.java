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

import com.privernal.dynamic.advanced.domain.DynamicDocTemplate;
import com.privernal.dynamic.advanced.domain.DynamicFormIoConfig;
import com.privernal.dynamic.advanced.domain.DynamicFormShare;
import com.privernal.dynamic.advanced.domain.DynamicLinkageLog;
import com.privernal.dynamic.advanced.domain.DynamicLinkageRule;
import com.privernal.dynamic.advanced.domain.DynamicShareReceipt;
import com.privernal.dynamic.advanced.mapper.DynamicDocTemplateMapper;
import com.privernal.dynamic.advanced.mapper.DynamicFormIoConfigMapper;
import com.privernal.dynamic.advanced.mapper.DynamicFormShareMapper;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageLogMapper;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageRuleMapper;
import com.privernal.dynamic.advanced.mapper.DynamicShareReceiptMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 高级设置配置级联清理（§8）：表单删除时清掉以 form_id 锚定的其余配置与派生数据。
 * 配置不进版本快照（§3.2），但必须随表单删除一并消失，否则残留外链还能被命中、
 * 残留规则还会对新表产生幽灵触发（tableCode 已被改名释放，规则匹配自然失效，但配置行必须清）。
 * 旧打印模板清理随 M0 下线移除；新单据打印模板（dynamic_doc_template）的级联
 * 挂回必须写在本类的 deleteByFormId 内（ObjectProvider 可选注入，另立挂点会静默失效）。
 */
@Slf4j
@Component
public class AdvancedConfigCleaner {

    @Autowired
    private DynamicFormIoConfigMapper ioConfigMapper;

    @Autowired
    private DynamicFormShareMapper shareMapper;

    @Autowired
    private DynamicShareReceiptMapper receiptMapper;

    @Autowired
    private DynamicLinkageRuleMapper linkageRuleMapper;

    @Autowired
    private DynamicDocTemplateMapper docTemplateMapper;

    @Autowired
    private DynamicLinkageLogMapper linkageLogMapper;

    public void deleteByFormId(String formId) {
        try {
            ioConfigMapper.delete(Wrappers.<DynamicFormIoConfig>lambdaQuery()
                    .eq(DynamicFormIoConfig::getFormId, formId));
            // 外链连带回执码映射；访问日志按 shareId 清
            List<DynamicFormShare> shares = shareMapper.selectList(Wrappers.<DynamicFormShare>lambdaQuery()
                    .eq(DynamicFormShare::getFormId, formId));
            for (DynamicFormShare share : shares) {
                receiptMapper.delete(Wrappers.<DynamicShareReceipt>lambdaQuery()
                        .eq(DynamicShareReceipt::getShareId, share.getId()));
                shareMapper.deleteById(share.getId());
            }
            linkageRuleMapper.delete(Wrappers.<DynamicLinkageRule>lambdaQuery()
                    .eq(DynamicLinkageRule::getFormId, formId));
            // 新单据打印模板级联挂回（plan-sheet-print §4.1〔v2 版补充〕）：表单删除时模板连软删。
            // 必须写在本方法内——挂点是 ObjectProvider 可选注入（DynamicFormServiceImpl:82-87），
            // 另立 cleaner/监听器会在 bean 缺失时静默跳过，留下孤儿模板行
            docTemplateMapper.delete(Wrappers.<DynamicDocTemplate>lambdaQuery()
                    .eq(DynamicDocTemplate::getFormId, formId));
            linkageLogMapper.delete(Wrappers.<DynamicLinkageLog>lambdaQuery()
                    .eq(DynamicLinkageLog::getFormId, formId));
        } catch (Exception e) {
            // 配置清理失败不阻断表单删除主链路（与 cancelScheduleTasks 同口径：记错误，残留可手工清）
            log.error("高级设置配置级联清理失败 formId={}", formId, e);
        }
    }
}
