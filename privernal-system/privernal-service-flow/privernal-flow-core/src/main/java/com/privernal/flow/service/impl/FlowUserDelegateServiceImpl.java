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

package com.privernal.flow.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.flow.domain.FlowUserDelegate;
import com.privernal.flow.mapper.FlowUserDelegateMapper;
import com.privernal.flow.service.IFlowUserDelegateService;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.feign.RemoteUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流程代理规则服务实现（R2-17）
 *
 * @author saperliu
 */
@Service
@DS("master")
public class FlowUserDelegateServiceImpl implements IFlowUserDelegateService {

    @Autowired
    private FlowUserDelegateMapper delegateMapper;

    @Autowired
    private RemoteUserService remoteUserService;

    @Override
    public List<FlowUserDelegate> list(String organizationId, String userId) {
        LambdaQueryWrapper<FlowUserDelegate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotEmpty(organizationId)) {
            wrapper.eq(FlowUserDelegate::getOrganizationId, organizationId);
        }
        if (StringUtils.isNotEmpty(userId)) {
            wrapper.eq(FlowUserDelegate::getUserId, userId);
        }
        wrapper.orderByDesc(FlowUserDelegate::getCreatedTime);
        return delegateMapper.selectList(wrapper);
    }

    @Override
    public FlowUserDelegate save(FlowUserDelegate delegate) {
        validate(delegate);
        delegate.setOrganizationId(StringUtils.isNotEmpty(delegate.getOrganizationId())
                ? delegate.getOrganizationId()
                : com.privernal.common.security.service.SecurityUtils.getOrganizationId());
        delegateMapper.insert(delegate);
        return delegate;
    }

    @Override
    public FlowUserDelegate edit(FlowUserDelegate delegate) {
        if (StringUtils.isEmpty(delegate.getId())) {
            throw new ServiceException("id 不能为空");
        }
        FlowUserDelegate existing = delegateMapper.selectById(delegate.getId());
        if (existing == null) {
            throw new ServiceException("代理规则不存在：" + delegate.getId());
        }
        validate(delegate);
        existing.setAgentId(delegate.getAgentId());
        existing.setStartTime(delegate.getStartTime());
        existing.setEndTime(delegate.getEndTime());
        existing.setStatus(delegate.getStatus());
        existing.setRemark(delegate.getRemark());
        delegateMapper.updateById(existing);
        return existing;
    }

    @Override
    public int remove(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("ids 不能为空");
        }
        return delegateMapper.deleteByIds(ids);
    }

    /**
     * 校验：人员存在、不可代理自己、同被代理人同代理人已启用规则不重复
     */
    private void validate(FlowUserDelegate delegate) {
        if (StringUtils.isEmpty(delegate.getUserId()) || StringUtils.isEmpty(delegate.getAgentId())) {
            throw new ServiceException("被代理人与代理人不能为空");
        }
        if (delegate.getUserId().equals(delegate.getAgentId())) {
            throw new ServiceException("代理人不能是被代理人本人");
        }
        for (String userId : List.of(delegate.getUserId(), delegate.getAgentId())) {
            CommonResult<SystemUserDTO> result = remoteUserService.findUserById(userId, "");
            if (result == null || result.getCode() != CommonResult.SUCCESS || result.getData() == null) {
                throw new ServiceException("用户不存在：" + userId);
            }
        }
        String status = StringUtils.isEmpty(delegate.getStatus()) ? FlowUserDelegate.STATUS_ENABLED : delegate.getStatus();
        delegate.setStatus(status);
        if (FlowUserDelegate.STATUS_ENABLED.equals(status)) {
            List<FlowUserDelegate> existing = delegateMapper.selectActiveByUserIds(
                    List.of(delegate.getUserId()), new java.util.Date());
            boolean duplicated = existing.stream().anyMatch(item ->
                    delegate.getAgentId().equals(item.getAgentId())
                            && (delegate.getId() == null || !delegate.getId().equals(item.getId())));
            if (duplicated) {
                throw new ServiceException("该用户已存在同代理人的启用中规则");
            }
        }
    }
}
