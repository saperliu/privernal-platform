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

package com.privernal.dynamic.dbs.service;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.flow.feign.RemoteFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author saperliu
 * @version 1.0.0
 * @Description 流程参与人的动态数据读取放行。
 * 审批人通常只有流程待办的权限、没有申请单所在动态表的按钮权限，
 * 但「查看自己经手的那张申请单」是审批的必需能力，所以按流程参与人身份放行只读
 * （发起人、曾任办理人/协办人、当前待办人）。判定走工作流的内部 Bean，无需额外接口。
 */
@Service
public class DynamicFlowAccessService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicFlowAccessService.class);

    @Autowired
    private RemoteFlowService remoteFlowService;

    /**
     * 当前登录人是否为 businessId 所在流程实例的参与人
     *
     * @param tableCode  业务数据所在的动态表单编码，需与实例解析出的表一致才放行
     * @param businessId 业务数据id（动态表主键）
     */
    public boolean isFlowParticipant(String tableCode, String businessId) {
        if (StringUtils.isEmpty(tableCode) || StringUtils.isEmpty(businessId)) {
            return false;
        }
        try {
            CommonResult<Boolean> result = remoteFlowService.isBusinessDataParticipant(tableCode, businessId);
            return result != null && result.getCode() == CommonResult.SUCCESS
                    && Boolean.TRUE.equals(result.getData());
        } catch (Exception e) {
            // 流程侧异常时按不放行处理，让调用方回到原有的按钮权限判定
            logger.warn("流程参与人判定失败 tableCode {} businessId {}", tableCode, businessId, e);
            return false;
        }
    }
}
