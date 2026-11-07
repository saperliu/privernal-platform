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

package com.privernal.flow.notice;

import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.mapper.DynamicFormMapper;
import com.privernal.message.constant.MessageConstant;
import com.privernal.message.dto.SystemMessageDTO;
import com.privernal.message.dto.SystemMessageUserDTO;
import com.privernal.message.service.ISystemMessageService;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.feign.RemoteUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程通知发送实现：桥接 flow-core 的 FlowNoticeSender 到平台站内信
 * 站内信通过系统消息中心展示（messageType=WORKFLOW），收件人昵称实时查询，查询失败降级为用户id
 *
 * @author saperliu
 */
@Component
public class SystemMessageFlowNoticeSender implements FlowNoticeSender {

    private static final Logger log = LoggerFactory.getLogger(SystemMessageFlowNoticeSender.class);

    private final ISystemMessageService systemMessageService;

    private final RemoteUserService remoteUserService;

    private final DynamicFormMapper dynamicFormMapper;

    public SystemMessageFlowNoticeSender(ISystemMessageService systemMessageService,
                                         RemoteUserService remoteUserService,
                                         DynamicFormMapper dynamicFormMapper) {
        this.systemMessageService = systemMessageService;
        this.remoteUserService = remoteUserService;
        this.dynamicFormMapper = dynamicFormMapper;
    }

    @Override
    public void send(FlowNoticeMessage notice) {
        if (notice == null || notice.getUserIds() == null || notice.getUserIds().isEmpty()) {
            return;
        }
        SystemMessageDTO message = new SystemMessageDTO();
        message.setMainTitle(notice.getTitle());
        message.setSubTitle(notice.getTitle());
        message.setMessageType(MessageConstant.MESSAGE_TYPE_WORKFLOW);
        message.setContent(notice.getContent());
        message.setDetailUrl(notice.getDetailUrl());
        message.setCreatedName(MessageConstant.CREATED_DEFAULT);
        // 消息必须归属组织：数据范围切面按 organization_id 过滤用户消息，空组织对所有用户不可见
        message.setOrganizationId(resolveOrganizationId(notice.getUserIds().get(0)));
        message.setDetailUrl(resolveDetailUrl(notice));
        message.setUserList(buildReceivers(notice));
        systemMessageService.saveSystemMessage(message);
    }

    /**
     * 消息跳转路径：流程绑定了动态表单（dynamic_form.workflow_code = flowCode）时
     * 直达业务表单页，未绑定（表单自定义流程）保持原流程页路径
     */
    private String resolveDetailUrl(FlowNoticeMessage notice) {
        String detailUrl = notice.getDetailUrl();
        if (StringUtils.isEmpty(notice.getFlowCode())) {
            return detailUrl;
        }
        try {
            DynamicForm form = dynamicFormMapper.selectOne(new LambdaQueryWrapper<DynamicForm>()
                    .eq(DynamicForm::getWorkflowCode, notice.getFlowCode())
                    .eq(DynamicForm::getDeletedFlag, "N")
                    .last("limit 1"));
            if (form != null && StringUtils.isNotEmpty(form.getTableCode())) {
                return "/builder/" + form.getTableCode();
            }
        } catch (Exception e) {
            log.warn("按流程编码查业务表单失败，消息跳转保持流程页 flowCode={}", notice.getFlowCode(), e);
        }
        return detailUrl;
    }

    /**
     * 解析收件人所属组织（取第一个收件人；流程消息收件人同属实例组织）
     */
    private String resolveOrganizationId(String userId) {
        try {
            CommonResult<SystemUserDTO> result = remoteUserService.findUserById(userId, Constants.SERVICE_REQUEST);
            if (result != null && result.getCode() == CommonResult.SUCCESS
                    && result.getData() != null && StringUtils.isNotEmpty(result.getData().getOrganizationId())) {
                return result.getData().getOrganizationId();
            }
        } catch (Exception e) {
            log.warn("查询用户组织失败，消息组织留空 userId={}", userId);
        }
        return "";
    }

    /**
     * 组装收件人（昵称查询失败时降级为用户id）
     */
    private List<SystemMessageUserDTO> buildReceivers(FlowNoticeMessage notice) {
        List<SystemMessageUserDTO> receivers = new ArrayList<>();
        for (String userId : notice.getUserIds()) {
            if (StringUtils.isEmpty(userId)) {
                continue;
            }
            SystemMessageUserDTO receiver = new SystemMessageUserDTO();
            receiver.setUserId(userId);
            receiver.setNickName(resolveNickName(userId));
            receivers.add(receiver);
        }
        return receivers;
    }

    private String resolveNickName(String userId) {
        try {
            CommonResult<SystemUserDTO> result = remoteUserService.findUserById(userId, Constants.SERVICE_REQUEST);
            if (result != null && result.getCode() == CommonResult.SUCCESS
                    && result.getData() != null && StringUtils.isNotEmpty(result.getData().getNickName())) {
                return result.getData().getNickName();
            }
        } catch (Exception e) {
            log.warn("查询用户昵称失败，降级为用户id userId={}", userId);
        }
        return userId;
    }
}
